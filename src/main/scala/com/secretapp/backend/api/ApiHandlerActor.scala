package com.secretapp.backend.api

import akka.actor.{ Actor, ActorRef, ActorLogging }
import akka.util.ByteString
import com.secretapp.backend.data.transport.MessageBox
import scodec.bits._
import com.secretapp.backend.data.transport.Package
import com.secretapp.backend.services.transport._
import com.secretapp.backend.services.common.PackageCommon
import com.secretapp.backend.services.common.PackageCommon._
import com.secretapp.backend.protocol.codecs._
import com.secretapp.backend.data._
import PackageCommon._
import scalaz._
import Scalaz._
import com.datastax.driver.core.{ Session => CSession }

// TODO: replace connection: ActorRef hack with real sender (or forget it?)
class ApiHandlerActor(connection: ActorRef, val clusterProxies: ClusterProxies)(implicit val session: CSession) extends Actor with ActorLogging
    with WrappedPackageService with PackageService {
  import akka.io.Tcp._

  var packageIndex = 0
  var closing = false

  var storage = Vector.empty[ByteString]
  var stored = 0L
  var transferred = 0L

  val maxStored = 100000000L // TODO: think about this value
  val highWatermark = maxStored * 5 / 10
  val lowWatermark = maxStored * 3 / 10

  var suspended = false

  val handleActor = self

  context watch connection

  def receive = writing

  def writing: Receive = {
    case PackageToSend(pe) =>
      wlog(s"PackageToSend($pe)")
      pe match {
        case \/-(p) =>
          val data = replyPackage(packageIndex, p)
          buffer(data)
          write(data)
        case -\/(p) =>
          val data = replyPackage(packageIndex, p)
          buffer(data)
          write(data)
          connection ! Close
      }

    case MessageBoxToSend(mb) =>
      wlog(s"MessageBoxToSend($mb)")
      val p = Package(getAuthId, getSessionId, mb)
      val data = replyPackage(packageIndex, p)
      buffer(data)
      write(data)

    case UpdateBoxToSend(ub) =>
      wlog(s"UpdateBoxToSend($ub)")
      // FIXME: real message id SA-32
      val p = Package(getAuthId, getSessionId, MessageBox(rand.nextLong, ub))
      val data = replyPackage(packageIndex, p)
      buffer(data)
      write(data)

    case m: ServiceMessage =>
      wlog(s"ServiceMessage: $m")
      serviceMessagesPF(m)

    case Received(data) =>
      wlog(s"Received: $data ${data.length}")
      handleByteStream(BitVector(data.toArray))(handlePackage, handleError)

    case PeerClosed =>
      wlog("Connection closed by peer")
      context stop self

    case CommandFailed(x) =>
      wlog(s"CommandFailed ${x}")

    case PackageAck(index) =>
      throw new Exception("Received ack in writing mode")
  }

  def buffering: Receive = {
    case PackageToSend(pe) =>
      blog(s"PackageToSend($pe)")
      pe match {
        case \/-(p) =>
          buffer(replyPackage(packageIndex, p))
        case -\/(p) =>
          buffer(replyPackage(packageIndex, p))
          closing = true
      }

    case MessageBoxToSend(mb) =>
      blog(s"MessageBoxToSend($mb)")
      val p = Package(getAuthId, getSessionId, mb)
      buffer(replyPackage(packageIndex, p))

    case UpdateBoxToSend(ub) =>
      blog(s"UpdateBoxToSend($ub)")
      // FIXME: real message id SA-32
      val p = Package(getAuthId, getSessionId, MessageBox(rand.nextLong, ub))
      buffer(replyPackage(packageIndex, p))

    case m: ServiceMessage =>
      blog(s"ServiceMessage: $m")
      serviceMessagesPF(m)

    case Received(data) =>
      blog(s"Received: $data ${data.length}")
      handleByteStream(BitVector(data.toArray))(handlePackage, handleError)

    case PeerClosed =>
      closing = true

    case CommandFailed(x) =>
      blog(s"CommandFailed ${x}")

    case PackageAck(index) =>
      blog(s"Ack ${index}")
      acknowledge()
  }

  private def write(data: ByteString, becomeBuffering: Boolean = true): Unit = {
    log.debug(s"Sending ${connection} ${data} ${packageIndex}")
    connection ! Write(data, PackageAck(packageIndex))
    packageIndex += 1
    if (becomeBuffering) {
      context.become(buffering, discardOld = false)
    }
  }

  private def buffer(data: ByteString): Unit = {
    storage :+= data
    stored += data.size

    if (stored > maxStored) {
      log.warning(s"drop connection to [connection] (buffer overrun)")
      context stop self

    } else if (stored > highWatermark) {
      log.debug(s"suspending reading")
      connection ! SuspendReading
      suspended = true
    }
  }

  private def acknowledge(): Unit = {
    require(storage.nonEmpty, "storage was empty")

    val size = storage(0).size
    stored -= size
    transferred += size

    storage = storage drop 1

    if (suspended && stored < lowWatermark) {
      log.debug("resuming reading")
      connection ! ResumeReading
      suspended = false
    }

    if (storage.isEmpty) {
      if (closing) {
        log.debug("stopping")
        context stop self
      } else {
        log.debug("resuming writing")
        context.unbecome()
      }
    } else write(storage(0), false)
  }

  def wlog(str: String) = log.info(s"[writing] ${str}")
  def blog(str: String) = log.info(s"[buffering] ${str}")
}
