package com.secretapp.backend.api

import akka.actor._
import akka.contrib.pattern.ClusterSingletonProxy
import akka.io.Tcp._
import akka.testkit._
import akka.util.{ ByteString, Timeout }
import akka.pattern.ask
import com.secretapp.backend.data._
import com.secretapp.backend.data.message._
import com.secretapp.backend.data.transport.MessageBox
import com.secretapp.backend.persist.CassandraSpecification
import com.secretapp.backend.protocol.codecs._
import com.secretapp.backend.protocol.codecs.message._
import com.secretapp.backend.api.counters._
import org.specs2.mutable.ActorLikeSpecification
import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.language.postfixOps
import org.specs2.mutable.ActorSpecification

class SocialBrokerSpec extends ActorLikeSpecification with CassandraSpecification with ImplicitSender {
  import system.dispatcher

  import SocialProtocol._

  override lazy val actorSystemName = "api"

  implicit val timeout = Timeout(Duration(30, "seconds"))

  "SocialBroker" should {
    "count relations" in {
      val authId1 = 1L
      val authId2 = 2L

      val region = SocialBroker.startRegion()

      region ! SocialMessageBox(authId1, RelationsNoted((1 to 100).toSet))

      region ! SocialMessageBox(authId2, RelationsNoted((42 to 100).toSet))

      region ! SocialMessageBox(authId1, RelationsNoted((99 to 150).toSet))

      region ! SocialMessageBox(authId2, RelationsNoted((99 to 3000).toSet))

      region ! SocialMessageBox(authId2, GetRelations)
      region ! SocialMessageBox(authId1, GetRelations)

      expectMsg((42 to 3000).toSet)
      expectMsg(Duration(30, "seconds"), (1 to 150).toSet)
    }
  }
}