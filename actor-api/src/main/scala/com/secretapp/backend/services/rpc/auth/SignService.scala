package com.secretapp.backend.services.rpc.auth

import akka.actor._
import akka.pattern.ask
import com.datastax.driver.core.{ Session => CSession }
import com.secretapp.backend.api.counters.CounterProtocol
import com.secretapp.backend.api.{ UpdatesBroker, ApiBrokerService, PhoneNumber}
import com.secretapp.backend.crypto.ec
import com.secretapp.backend.data.message.rpc._
import com.secretapp.backend.data.message.rpc.auth._
import com.secretapp.backend.data.message.struct
import com.secretapp.backend.data.message.update.{ NewDevice, RemoveDevice }
import com.secretapp.backend.data.message.update.contact.ContactRegistered
import com.secretapp.backend.models
import com.secretapp.backend.helpers.SocialHelpers
import com.secretapp.backend.persist
import com.secretapp.backend.sms.ClickatellSmsEngineActor
import scala.collection.immutable
import scala.concurrent.Future
import scala.util.{ Success, Failure }
import scodec.bits.BitVector
import scalaz._
import Scalaz._
import shapeless._
import Function.tupled
import com.secretapp.backend.api.rpc.RpcValidators._

trait SignService extends SocialHelpers {
  self: ApiBrokerService =>
  implicit val session: CSession

  import context._
  import UpdatesBroker._

  def handleRpcAuth: PartialFunction[RpcRequestMessage, \/[Throwable, Future[RpcResponse]]] = {
    case r: RequestAuthCode =>
      unauthorizedRequest {
        handleRequestAuthCode(r.phoneNumber, r.appId, r.apiKey)
      }
    case r: RequestSignIn =>
      unauthorizedRequest {
        handleSign(
          r.phoneNumber, r.smsHash, r.smsCode, r.publicKey,
          r.deviceHash, r.deviceTitle, r.appId, r.appKey
        )(r.left)
      }
    case r: RequestSignUp =>
      unauthorizedRequest {
        handleSign(
          r.phoneNumber, r.smsHash, r.smsCode, r.publicKey,
          r.deviceHash, r.deviceTitle, r.appId, r.appKey
        )(r.right)
      }
    case r: RequestGetAuth =>
      authorizedRequest {
        handleRequestGetAuth()
      }
    case RequestRemoveAuth(id) =>
      authorizedRequest {
        handleRequestRemoveAuth(id)
      }
    case r: RequestRemoveAllOtherAuths =>
      authorizedRequest {
        handleRequestRemoveAllOtherAuths()
      }
    case r: RequestLogout =>
      authorizedRequest {
        handleRequestLogout()
      }
  }

  def handleRequestGetAuth(): Future[RpcResponse] = {
    for {
      authItems <- persist.AuthItem.getEntities(currentUser.get.uid)
    } yield {
      Ok(ResponseGetAuth(authItems.toVector map (struct.AuthItem.fromModel(_, currentUser.get.authId))))
    }
  }

  def handleRequestLogout(): Future[RpcResponse] = {
    persist.AuthItem.getEntityByUserIdAndPublicKeyHash(currentUser.get.uid, currentUser.get.publicKeyHash) flatMap {
      case Some(authItem) =>
        logout(authItem, currentUser.get) map { _ =>
          Ok(ResponseVoid())
        }
      case None =>
        Future.successful(Error(404, "USER_NOT_FOUND", "User not found", false))
    }
  }

  def handleRequestRemoveAuth(id: Int): Future[RpcResponse] = {
    persist.AuthItem.getEntity(currentUser.get.uid, id) flatMap {
      case Some(authItem) =>
        logout(authItem, currentUser.get) map { _ =>
          Ok(ResponseVoid())
        }
      case None =>
        Future.successful(Error(404, "USER_NOT_FOUND", "User not found", false))
    }
  }

  def handleRequestRemoveAllOtherAuths(): Future[RpcResponse] = {
    persist.AuthItem.getEntities(currentUser.get.uid) map { authItems =>
      authItems foreach {
        case authItem =>
          if (authItem.authId != currentUser.get.authId) {
            logout(authItem, currentUser.get)
          }
      }

      Ok(ResponseVoid())
    }
  }

  def handleRequestAuthCode(phoneNumberRaw: Long, appId: Int, apiKey: String): Future[RpcResponse] = {
    PhoneNumber.normalizeLong(phoneNumberRaw) match {
      case None =>
        Future.successful(Error(400, "PHONE_NUMBER_INVALID", "", true))
      case Some(phoneNumber) =>
        val smsPhoneTupleFuture = for {
          smsR <- persist.AuthSmsCode.getEntity(phoneNumber)
          phoneR <- persist.Phone.getEntity(phoneNumber)
        } yield (smsR, phoneR)
        smsPhoneTupleFuture flatMap { case (smsR, phoneR) =>
          smsR match {
            case Some(models.AuthSmsCode(_, sHash, _)) =>
              Future.successful(Ok(ResponseAuthCode(sHash, phoneR.isDefined)))
            case None =>
              val smsHash = genSmsHash
              val smsCode = phoneNumber.toString match {
                case strNumber if strNumber.startsWith("7555") => strNumber(4).toString * 4
                case _ => genSmsCode
              }
              singletons.smsEngine ! ClickatellSmsEngineActor.Send(phoneNumber, smsCode) // TODO: move it to actor with persistence
              for { _ <- persist.AuthSmsCode.insertEntity(models.AuthSmsCode(phoneNumber, smsHash, smsCode)) }
              yield Ok(ResponseAuthCode(smsHash, phoneR.isDefined))
          }
        }
    }
  }

  private def handleSign(
    phoneNumberRaw: Long, smsHash: String, smsCode: String, publicKey: BitVector,
    deviceHash: BitVector, deviceTitle: String, appId: Int, appKey: String
  )(m: RequestSignIn \/ RequestSignUp): Future[RpcResponse] = {
    val authId = currentAuthId // TODO
    PhoneNumber.normalizeWithCountry(phoneNumberRaw) match {
      case None =>
        Future.successful(Error(400, "PHONE_NUMBER_INVALID", "", true))
      case Some((phoneNumber, countryCode)) =>
        @inline
        def auth(u: models.User): Future[RpcResponse] = {
          persist.AuthSmsCode.dropEntity(phoneNumber)
          log.info(s"Authenticate currentUser=$u")
          this.currentUser = Some(u)

          persist.AuthItem.getEntitiesByUserIdAndDeviceHash(u.uid, deviceHash) map { authItems =>
            for (authItem <- authItems) {
              logoutKeepingCurrentAuthIdAndPK(authItem, currentUser.get)
            }

            persist.AuthItem.insertEntity(
              models.AuthItem.build(
                id = rand.nextInt, appId = appId, deviceTitle = deviceTitle, authTime = (System.currentTimeMillis / 1000).toInt,
                authLocation = "", latitude = None, longitude = None,
                authId = u.authId, publicKeyHash = u.publicKeyHash, deviceHash = deviceHash
              ), u.uid
            )

            Ok(ResponseAuth(u.publicKeyHash, struct.User.fromModel(u, authId), struct.Config(300)))
          }
        }

        @inline
        def signIn(userId: Int): Future[RpcResponse] = {
          val publicKeyHash = ec.PublicKey.keyHash(publicKey)

          @inline
          def updateUserRecord(name: String): Unit = {
            persist.User.insertEntityRowWithChildren(userId, authId, publicKey, publicKeyHash, phoneNumber, name, countryCode) onSuccess {
              case _ => pushNewDeviceUpdates(authId, userId, publicKeyHash, publicKey)
            }
          }

          @inline
          def getUserName(name: String) = m match {
            case \/-(req) => req.name
            case _ => name
          }

          // TODO: use sequence from shapeless-contrib

          val (fuserAuthR, fuserR) = (
            persist.User.getEntity(userId, authId),
            persist.User.getEntity(userId) // remove it when it cause bottleneck
            )

          fuserAuthR flatMap { userAuthR =>
            fuserR flatMap { userR =>
              if (userR.isEmpty) Future.successful(Error(400, "INTERNAL_ERROR", "", true))
              else userAuthR match {
                case None =>
                  val user = userR.get
                  val userName = getUserName(user.name)
                  updateUserRecord(userName)
                  val keyHashes = user.keyHashes + publicKeyHash
                  val newUser = user.copy(authId = authId, publicKey = publicKey, publicKeyHash = publicKeyHash,
                    keyHashes = keyHashes, name = userName)
                  auth(newUser)
                case Some(userAuth) =>
                  val userName = getUserName(userAuth.name)
                  if (userAuth.publicKey != publicKey) {
                    updateUserRecord(userName)
                    persist.User.removeKeyHash(userAuth.uid, userAuth.publicKeyHash, Some(currentUser.get.authId)) flatMap { _ =>
                      val keyHashes = userAuth.keyHashes.filter(_ != userAuth.publicKeyHash) + publicKeyHash
                      val newUser = userAuth.copy(publicKey = publicKey, publicKeyHash = publicKeyHash, keyHashes = keyHashes,
                        name = userName)
                      auth(newUser)
                    }
                  } else {
                    if (userAuth.name != userName) {
                      persist.User.updateName(userAuth.uid, userName)
                      auth(userAuth.copy(name = userName))
                    } else auth(userAuth)
                  }
              }
            }
          }
        }

        if (smsCode.isEmpty) Future.successful(Error(400, "PHONE_CODE_EMPTY", "", false))
        else if (publicKey.length == 0) Future.successful(Error(400, "INVALID_KEY", "", false))
        else {
          val f = for {
            smsCodeR <- persist.AuthSmsCode.getEntity(phoneNumber)
            phoneR <- persist.Phone.getEntity(phoneNumber)
          } yield (smsCodeR, phoneR)
          f flatMap tupled {
            (smsCodeR, phoneR) =>
              if (smsCodeR.isEmpty) Future.successful(Error(400, "PHONE_CODE_EXPIRED", s"$phoneNumber $phoneNumberRaw", false))
              else smsCodeR.get match {
                case s if s.smsHash != smsHash => Future.successful(Error(400, "PHONE_CODE_EXPIRED", "", false))
                case s if s.smsCode != smsCode => Future.successful(Error(400, "PHONE_CODE_INVALID", "", false))
                case _ =>
                  m match {
                    case -\/(_: RequestSignIn) => phoneR match {
                      case None => Future.successful(Error(400, "PHONE_NUMBER_UNOCCUPIED", "", false))
                      case Some(rec) =>
                        persist.AuthSmsCode.dropEntity(phoneNumber)
                        signIn(rec.userId) // user must be persisted before sign in
                    }
                    case \/-(req: RequestSignUp) =>
                      persist.AuthSmsCode.dropEntity(phoneNumber)
                      phoneR match {
                        case None => withValidName(req.name) { name =>
                          withValidPublicKey(publicKey) { publicKey =>
                            val pkHash = ec.PublicKey.keyHash(publicKey)
                            val user = models.User(
                              uid = rand.nextInt,
                              authId = authId,
                              publicKey = publicKey,
                              publicKeyHash = pkHash,
                              phoneNumber = phoneNumber,
                              accessSalt = genUserAccessSalt,
                              name = name,
                              sex = models.NoSex,
                              countryCode = countryCode,
                              keyHashes = immutable.Set(pkHash))
                            persist.User.insertEntityWithChildren(user) flatMap { _ =>
                              pushContactRegisteredUpdates(user)
                              auth(user)
                            }
                          }
                        }
                        case Some(rec) =>
                          signIn(rec.userId)
                      }
                  }
              }
          }
        }
    }
  }

  private def pushRemoveDeviceUpdates(userId: Int, publicKeyHash: Long): Unit = {
    getRelations(userId) onComplete {
      case Success(userIds) =>
        for (targetUserId <- userIds) {
          getAuthIds(targetUserId) onComplete {
            case Success(authIds) =>
              for (targetAuthId <- authIds) {
                updatesBrokerRegion ! NewUpdatePush(targetAuthId, RemoveDevice(userId, publicKeyHash))
              }
            case Failure(e) =>
              log.error(s"Failed to get authIds for uid=$targetUserId to push RemoveDevice update")
              throw e
          }
        }
      case Failure(e) =>
        log.error(s"Failed to get relations to push RemoveDevice updates userId=$userId $publicKeyHash")
        throw e
    }
  }

  private def pushNewDeviceUpdates(authId: Long, userId: Int, publicKeyHash: Long, publicKey: BitVector): Unit = {
    // Push NewFullDevice updates
    persist.UserPublicKey.fetchAuthIdsByUserId(userId) onComplete {
      case Success(authIds) =>
        for (targetAuthId <- authIds) {
          if (targetAuthId != authId) {
            updatesBrokerRegion ! NewUpdatePush(targetAuthId, NewDevice(userId, publicKeyHash, publicKey.some, System.currentTimeMillis()))
          }
        }
      case Failure(e) =>
        log.error(s"Failed to get authIds for authId=$authId uid=$userId to push NewFullDevice updates")
        throw e
    }

    // Push NewDevice updates
    getRelations(userId) onComplete {
      case Success(userIds) =>
        for (targetUserId <- userIds) {
          persist.UserPublicKey.fetchAuthIdsByUserId(targetUserId) onComplete {
            case Success(authIds) =>
              for (targetAuthId <- authIds) {
                updatesBrokerRegion ! NewUpdatePush(targetAuthId, NewDevice(userId, publicKeyHash, None, System.currentTimeMillis()))
              }
            case Failure(e) =>
              log.error(s"Failed to get authIds for authId=$authId uid=$targetUserId to push new device updates $publicKeyHash")
              throw e
          }
        }
      case Failure(e) =>
        log.error(s"Failed to get relations to push new device updates authId=$authId uid=$userId $publicKeyHash")
        throw e
    }
  }

  private def pushContactRegisteredUpdates(u: models.User): Unit = {
    import com.secretapp.backend.api.SocialProtocol._

    persist.UnregisteredContact.byNumber(u.phoneNumber) map { contacts =>
      contacts foreach { c =>
        socialBrokerRegion ! SocialMessageBox(u.uid, RelationsNoted(Set(c.ownerUserId)))

        getAuthIds(c.ownerUserId) map { authIds =>
          authIds foreach { authId =>
            pushUpdate(authId, ContactRegistered(u.uid, false, System.currentTimeMillis()))
          }
        }
      }
      persist.UnregisteredContact.removeEntity(u.phoneNumber)
    }
  }

  private def logout(authItem: models.AuthItem, currentUser: models.User)(implicit session: CSession) = {
    // TODO: use sequence from shapeless-contrib after being upgraded to scala 2.11
    Future.sequence(Seq(
      persist.AuthId.deleteEntity(authItem.authId),
      persist.User.removeKeyHash(currentUser.uid, authItem.publicKeyHash, Some(currentUser.authId)),
      persist.AuthItem.setDeleted(currentUser.uid, authItem.id)
    )) andThen {
      case Success(_) =>
        pushRemoveDeviceUpdates(currentUser.uid, authItem.publicKeyHash)
    }
  }

  private def logoutKeepingCurrentAuthIdAndPK(authItem: models.AuthItem, currentUser: models.User)(implicit session: CSession) = {
    val frmAuthId = if (currentUser.authId != authItem.authId) {
      persist.AuthId.deleteEntity(authItem.authId)
    } else {
      Future.successful()
    }

    val frmKeyHash = if (currentUser.publicKeyHash != authItem.publicKeyHash) {
      persist.User.removeKeyHash(currentUser.uid, authItem.publicKeyHash, Some(currentUser.authId))
    } else {
      Future.successful()
    }

    // TODO: use sequence from shapeless-contrib after being upgraded to scala 2.11
    Future.sequence(Seq(
      frmKeyHash,
      frmAuthId,
      persist.AuthItem.setDeleted(currentUser.uid, authItem.id)
    )) andThen {
      case Success(_) =>
        pushRemoveDeviceUpdates(currentUser.uid, authItem.publicKeyHash)
    }
  }
}