package com.secretapp.backend.api.rpc

import akka.actor._
import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import com.datastax.driver.core.{ Session => CSession }
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import com.secretapp.backend.api.SocialProtocol
import com.secretapp.backend.api.UpdatesBroker
import com.secretapp.backend.data.message.rpc.ResponseVoid
import com.secretapp.backend.data.message.rpc.messaging._
import com.secretapp.backend.data.message.rpc.{ Error, Ok, RpcResponse }
import com.secretapp.backend.data.message.rpc.{ update => updateProto }
import com.secretapp.backend.data.message.RpcResponseBox
import com.secretapp.backend.data.message.update.MessageReceived
import com.secretapp.backend.data.models.User
import com.secretapp.backend.data.transport.MTPackage
import com.secretapp.backend.persist.{ UserPublicKeyRecord, UserRecord }
import com.secretapp.backend.services.common.PackageCommon._
import java.util.UUID
import scala.collection.concurrent.TrieMap
import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.Success
import scalaz._
import scalaz.Scalaz._
import scodec.bits._
import scodec.codecs.uuid

class MessagingServiceActor(val updatesBrokerRegion: ActorRef, val socialBrokerRegion: ActorRef, val currentUser: User)(implicit val session: CSession) extends Actor with ActorLogging with MessagingService {
  import context.{ system, become, dispatcher }

  implicit val timeout = Timeout(5.seconds)

  val counterId = currentUser.authId.toString

  val randomIds = new ConcurrentLinkedHashMap.Builder[Long, Boolean]
    .initialCapacity(10).maximumWeightedCapacity(100).build

  def receive: Actor.Receive = {
    case RpcProtocol.Request(RequestMessageReceived(uid, randomId, accessHash)) =>
      val replyTo = sender()
      handleRequestMessageReceived(uid, randomId, accessHash) pipeTo replyTo

    case RpcProtocol.Request(RequestSendMessage(_, _, _, _, _, messages)) if messages.length == 0 =>
      sender ! Error(400, "ZERO_MESSAGES_LENGTH", "Messages lenght is zero.", false)

    case RpcProtocol.Request(RequestSendMessage(uid, accessHash, randomId, useAesKey, aesMessage, messages)) =>
      val replyTo = sender()

      Option(randomIds.get(randomId)) match {
        case Some(_) =>
          replyTo ! Error(409, "MESSAGE_ALREADY_SENT", "Message with the same randomId has been already sent.", false)
        case None =>
          randomIds.put(randomId, true)
          val f = handleRequestSendMessage(uid, accessHash, randomId, useAesKey, aesMessage, messages) map { res =>
            replyTo ! res
          }
          f onFailure {
            case err =>
              replyTo ! Error(400, "INTERNAL_SERVER_ERROR", err.getMessage, true)
              randomIds.remove(randomId)
              log.error(s"Failed to send message ${err}")
          }
      }
  }
}

sealed trait MessagingService {
  self: MessagingServiceActor =>

  import context.{ dispatcher, system }
  import UpdatesBroker._

  // Stores (userId, publicKeyHash) -> authId associations
  // TODO: migrate to ConcurrentLinkedHashMap
  val authIds = new TrieMap[(Int, Long), Future[Option[Long]]]

  // Caches userId -> accessHash associations
  val usersCache = new ConcurrentLinkedHashMap.Builder[Int, immutable.Seq[User]]
    .initialCapacity(10).maximumWeightedCapacity(100).build

  protected def handleRequestMessageReceived(uid: Int, randomId: Long, accessHash: Long): Future[RpcResponse] = {
    val fUsers = Option(usersCache.get(uid)) match {
      case Some(users) =>
        Future.successful(users)
      case None =>
        UserRecord.getEntities(uid)
    }

    fUsers flatMap {
      case users if users.isEmpty =>
        Future.successful(Error(404, "USER_DOES_NOT_EXISTS", "User does not exists.", true))
      case users =>
        val user = users.head

        if (user.accessHash(currentUser.authId) == accessHash) {
          users map { u =>
            updatesBrokerRegion ! NewUpdatePush(u.authId, MessageReceived(currentUser.uid, randomId))
          }
          for {
            seq <- ask(updatesBrokerRegion, UpdatesBroker.GetSeq(currentUser.authId)).mapTo[Int]
          } yield Ok(ResponseMessageReceived(seq))
        } else {
          Future.successful(Error(401, "ACCESS_HASH_INVALID", "Invalid access hash.", false))
        }
    }
  }

  protected def handleRequestSendMessage(uid: Int,
    accessHash: Long,
    randomId: Long,
    useAesKey: Boolean,
    aesMessage: Option[BitVector],
    messages: Seq[EncryptedMessage]): Future[RpcResponse] = {
    // TODO: check accessHash SA-21

    @inline
    def authIdFor(uid: Int, publicKeyHash: Long): Future[Option[Long]] = {
      log.debug(s"Resolving authId for ${uid} ${publicKeyHash}")
      authIds.get((uid, publicKeyHash)) match {
        case Some(f) =>
          log.debug(s"Resolved(cache) authId for ${uid} ${publicKeyHash}")
          f
        case None =>
          val f = UserPublicKeyRecord.getAuthIdByUidAndPublicKeyHash(uid, publicKeyHash)
          authIds.put((uid, publicKeyHash), f)
          f onSuccess { case _ => log.debug(s"Resolved authId for ${uid} ${publicKeyHash}") }
          f
      }
    }

    @inline
    def pushUpdates(): Unit = {
      val uids = messages map { message =>
        authIdFor(message.uid, message.publicKeyHash) onComplete {
          case Success(Some(authId)) =>
            log.info(s"Pushing message ${message}")
            updatesBrokerRegion ! NewUpdateEvent(authId, NewMessage(currentUser.uid, uid, message, aesMessage))
          case x =>
            log.error(s"Cannot find authId for uid=${message.uid} publicKeyHash=${message.publicKeyHash} ${x}")
        }
        message.uid
      }
    }

    UserRecord.getEntity(uid) flatMap {
      case Some(destUserEntity) =>
        val updatesDestUserId = destUserEntity.uid
        val updatesDestPublicKeyHash = destUserEntity.publicKeyHash

        // Record relation between receiver authId and sender uid
        log.debug(s"Recording relation uid=${uid} -> uid=${currentUser.uid}")
        socialBrokerRegion ! SocialProtocol.SocialMessageBox(
          uid, SocialProtocol.RelationsNoted(Set(currentUser.uid)))

        pushUpdates()

        // FIXME: handle failures (retry or error, should not break seq)
        for {
          s <- ask(
            updatesBrokerRegion, NewUpdateEvent(currentUser.authId, NewMessageSent(randomId))).mapTo[UpdatesBroker.StrictState]
        } yield {
          log.debug("Replying")
          val rsp = ResponseSendMessage(mid = s._2, seq = s._1, state = s._3)
          Ok(rsp)
        }
      case None =>
       Future.successful(Error(400, "INTERNAL_ERROR", "Destination user not found", true))
    }
  }
}
