package com.secretapp.backend.protocol.transport

import akka.actor._
import akka.event.LoggingAdapter
import com.datastax.driver.core.{ Session => CSession }
import com.secretapp.backend.data.message.{ Drop, NewSession, TransportMessage }
import com.secretapp.backend.data.models.{ AuthId, User }
import com.secretapp.backend.data.transport.MTPackage
import com.secretapp.backend.data.transport.MessageBox
import com.secretapp.backend.persist.AuthIdRecord
import com.secretapp.backend.protocol.codecs.message.MessageBoxCodec
import com.secretapp.backend.services.{ GeneratorService, SessionManager, UserManagerService }
import com.secretapp.backend.session.SessionProtocol
import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.Future
import scala.util.{ Failure, Success }
import scalaz._
import scalaz.Scalaz._

trait PackageManagerService extends UserManagerService with GeneratorService with SessionManager {
  self: Connector =>
  import context._

  implicit val session: CSession

  protected var currentAuthId: Long = _
  private var currentSessionId: Long = _
  private val currentSessions = new ConcurrentLinkedQueue[Long]()

  def log: LoggingAdapter

  // TODO: move to KeyFrontend!
  private def checkPackageAuth(p: MTPackage, mb: MessageBox)(f: (MTPackage, Option[TransportMessage]) => Unit): Future[Any] = {
    log.debug(s"Checking package auth ${p}")
    if (p.authId == 0L) { // check for auth request - simple key registration
      if (p.sessionId == 0L) {
        val newAuthId = genNewAuthId
        AuthIdRecord.insertEntity(AuthId(newAuthId, None)) andThen {
          case Success(_) =>
            currentAuthId = newAuthId

            f(p, None)
          case Failure(e) => sendDrop(p, 0, e)
        }
      } else {
        sendDrop(p, 0, s"unknown session id(${p.sessionId}) within auth id(${p.authId}})")
      }
    } else {
      val fres = AuthIdRecord.getEntity(p.authId) flatMap {
        case Some(authIdRecord) =>
          currentAuthId = authIdRecord.authId
          authIdRecord.user flatMap { optUser =>
            currentUser = optUser

            // FIXME: don't do it on each request!
            currentUser map { user =>
              log.debug(s"sending AuthorizeUser ${p.authId} ${p.sessionId} $user")
              sessionRegion ! SessionProtocol.Envelope(p.authId, p.sessionId, SessionProtocol.AuthorizeUser(user))
            }

            log.debug(s"Handling authenticated package currentAuthId=${currentAuthId} currentUser=${currentUser} ${p}")
            handlePackageAuthentication(p, mb)(f)
          }
        case None =>
          sendDrop(p, 0, s"unknown auth id(${p.authId}) or session id(${p.sessionId})")
      }

      fres recover {
        case e: Throwable =>
          sendDrop(p, 0, s"ERROR ${e}") // TODO: humanize error
      }
    }
  }

  final def handlePackageAuthentication(p: MTPackage, mb: MessageBox)(f: (MTPackage, Option[TransportMessage]) => Unit): Future[Any] = {
    if (currentAuthId == 0L) { // check for empty auth id - it mean a new connection
      checkPackageAuth(p, mb)(f)
    } else {
      checkPackageSession(p, mb)(f)
    }
  }

  private def checkPackageSession(p: MTPackage, mb: MessageBox)(f: (MTPackage, Option[TransportMessage]) => Unit): Future[Any] = {
    log.debug(s"Checking package session currentUser=${currentUser} currentAuthId=${currentAuthId} package=${p}")

    @inline
    def updateCurrentSession(sessionId: Long): Unit = {
      log.info(s"updateCurrentSession $sessionId")
      if (currentSessionId == 0L) {
        currentSessionId = sessionId
      }
      sessionRegion ! SessionProtocol.Envelope(currentAuthId, sessionId, SessionProtocol.NewConnection(context.self))
      currentSessions.add(sessionId)
    }

    if (p.authId == currentAuthId) {
      if (p.sessionId == 0L) {
        sendDrop(p, mb.messageId, "sessionId can't be zero")
      } else {
        if (p.sessionId == currentSessionId || currentSessions.contains(p.sessionId)) {
          Future.successful(f(p, None))
        } else {
          getOrCreateSession(p.authId, p.sessionId) andThen {
            case Success(ms) => ms match {
              case Left(sessionId) =>
                updateCurrentSession(sessionId)
                f(p, None)
              case Right(sessionId) =>
                updateCurrentSession(sessionId)
                f(p, Some(NewSession(sessionId, mb.messageId)))
            }
            case Failure(e) => sendDrop(p, mb.messageId, e)
          }
        }
      }
    } else {
      sendDrop(p, mb.messageId, "you can't use two different auth id at the same connection")
    }
  }

  private def sendDrop(p: MTPackage, messageId: Long, msg: String): Future[Unit] = {
    val drop = Drop(messageId, msg)
    val reply = p.replyWith(messageId, drop).left
    log.warning(s"Sending drop $drop")
    context.self ! reply
    Future.successful()
  }

  protected def sendDrop(p: MTPackage, messageId: Long, e: Throwable): Future[Unit] = sendDrop(p, messageId, e.getMessage)
}
