package com.secretapp.backend.api.history

import com.secretapp.backend.api.{ AvatarSpecHelpers, GroupSpecHelpers, MessagingSpecHelpers }
import com.secretapp.backend.data.message._
import com.secretapp.backend.data.message.struct
import com.secretapp.backend.data.message.rpc._
import com.secretapp.backend.data.message.rpc.messaging._
import com.secretapp.backend.data.message.rpc.history._
import com.secretapp.backend.data.message.rpc.update._
import com.secretapp.backend.models
import com.secretapp.backend.persist
import com.secretapp.backend.services.rpc.RpcSpec
import com.secretapp.backend.util.ACL
import im.actor.testkit.ActorSpecification
import org.specs2.specification.Step

class HistorySpec extends RpcSpec with MessagingSpecHelpers with GroupSpecHelpers with AvatarSpecHelpers {
  object sqlDb extends sqlDb

  override def is = sequential ^ s2"""
    RequestDeleteMessage handler should
      respond with ResponseVoid         ${cases.deleteMessages.e1}
      delete message from history       ${cases.deleteMessages.e2}
    ServiceMessage should be generated at
      RequestCreateGroup       -> GroupCreatedEx                 ${cases.serviceMessages.groupCreated}
      RequestKickUser          -> UserKickedEx                   ${cases.serviceMessages.userKicked}
      RequestInviteUser        -> UserAddedEx                    ${cases.serviceMessages.userAdded}
      RequestChangeGroupTitle  -> GroupChangedTitleEx            ${cases.serviceMessages.changedTitle}
      RequestChangeGroupAvatar -> GroupChangedAvatarEx           ${cases.serviceMessages.changedAvatar}
      RequestRemoveGroupAvatar -> GroupChangedAvatarEx           ${cases.serviceMessages.removedAvatar}
      RequestLeaveGroup        -> UserLeftEx (no dialog reorder) ${cases.serviceMessages.userLeft}
  """

  object cases extends sqlDb {
    def loadHistory(outPeer: struct.OutPeer, date: Long, limit: Int)(implicit scope: TestScope): ResponseLoadHistory = {
      val (rsp, _) = RequestLoadHistory(outPeer, date, limit) :~> <~:[ResponseLoadHistory]
      rsp
    }

    def loadDialogs(date: Long, limit: Int)(implicit scope: TestScope): ResponseLoadDialogs = {
      val (rsp, _) = RequestLoadDialogs(date, limit) :~> <~:[ResponseLoadDialogs]
      rsp
    }

    object deleteMessages {
      val (scope1, scope2) = TestScope.pair(rand.nextInt, rand.nextInt)
      catchNewSession(scope1)

      val outPeer = struct.OutPeer.privat(scope2.user.uid, ACL.userAccessHash(scope1.user.authId, scope2.user))

      def e1 = {
        using(scope1) { implicit scope =>
          RequestSendMessage(
            outPeer = outPeer,
            randomId = 1L,
            message = TextMessage("Yolo1")
          ) :~> <~:[ResponseSeqDate]

          RequestSendMessage(
            outPeer = outPeer,
            randomId = 2L,
            message = TextMessage("Yolo2")
          ) :~> <~:[ResponseSeqDate]

          RequestSendMessage(
            outPeer = outPeer,
            randomId = 3L,
            message = TextMessage("Yolo3")
          ) :~> <~:[ResponseSeqDate]

          RequestDeleteMessage(outPeer, Vector(1L, 3L)) :~> <~:[ResponseVoid]
        }
      }

      def e2 = {
        Thread.sleep(1000)

        using(scope1) { implicit scope =>
          val respHistory = loadHistory(outPeer, 0L, 3)
          respHistory.history.length should_==(1)
          respHistory.history.head.randomId should_==(2L)
        }
      }
    }

    object serviceMessages {
      val (scope1, scope2) = TestScope.pair(rand.nextInt, rand.nextInt)
      catchNewSession(scope1)
      catchNewSession(scope2)

      val respGroup = createGroup(Vector(scope2.user))(scope1)
      val avatarFileLocation = storeAvatarFiles(fileAdapter)._1

      def groupCreated = {
        val smsg = ServiceMessage("Group created", Some(GroupCreatedExtension()))

        using(scope1) { implicit s =>
          sendMessage(scope2.user)
          Thread.sleep(200)

          val respHistory = loadHistory(respGroup.groupPeer.asOutPeer, 0l, 100)
          respHistory.history.length should_==(1)
          respHistory.history.head.message should_==(smsg)

          val respDialogs = loadDialogs(0, 100)
          val dialogs = respDialogs.dialogs

          dialogs.length should_==(2)
          dialogs.last.peer should_==(struct.Peer.group(respGroup.groupPeer.id))
        }

        using(scope2) { implicit s =>
          val respHistory = loadHistory(respGroup.groupPeer.asOutPeer, 0l, 100)
          respHistory.history.length should_==(1)
          respHistory.history.head.message should_==(smsg)

          val respDialogs = loadDialogs(0, 100)
          val dialogs = respDialogs.dialogs

          dialogs.length should_==(2)
          dialogs.last.peer should_==(struct.Peer.group(respGroup.groupPeer.id))
        }
      }

      def userKicked = {
        val smsg = ServiceMessage("User kicked from the group", Some(UserKickedExtension(scope2.user.uid)))

        using(scope1) { implicit s =>
          kickUser(respGroup.groupPeer, scope2.user)

          Thread.sleep(500)

          val respHistory = loadHistory(respGroup.groupPeer.asOutPeer, 0l, 100)
          respHistory.history.length should_==(2)
          val msg = respHistory.history.head
          msg.senderUserId should_==(scope1.user.uid)
          msg.message should_==(smsg)
        }

        using(scope2) { implicit s =>
          val respHistory = loadHistory(respGroup.groupPeer.asOutPeer, 0l, 100)
          respHistory.history.length should_==(2)
          val msg = respHistory.history.head
          msg.senderUserId should_==(scope1.user.uid)
          msg.message should_==(smsg)
        }
      }

      def userAdded = {
        val smsg = ServiceMessage("User added to the group", Some(UserAddedExtension(scope2.user.uid)))

        using(scope1) { implicit s =>
          sendMessage(scope2.user)
          Thread.sleep(100)

          inviteUser(respGroup.groupPeer, scope2.user)
          Thread.sleep(100)

          val respHistory = loadHistory(respGroup.groupPeer.asOutPeer, 0l, 100)
          respHistory.history.length should_==(3)
          val msg = respHistory.history.head
          msg.senderUserId should_==(scope1.user.uid)
          msg.message should_==(smsg)

          val respDialogs = loadDialogs(0, 100)
          val dialogs = respDialogs.dialogs

          dialogs.length should_==(2)
          dialogs.head.peer should_==(struct.Peer.group(respGroup.groupPeer.id))
          dialogs.head.message should_==(smsg)
        }

        using(scope2) { implicit s =>
          val respHistory = loadHistory(respGroup.groupPeer.asOutPeer, 0l, 100)
          respHistory.history.length should_==(3)
          val msg = respHistory.history.head
          msg.senderUserId should_==(scope1.user.uid)
          msg.message should_==(smsg)

          val respDialogs = loadDialogs(0, 100)
          val dialogs = respDialogs.dialogs

          dialogs.length should_==(2)
          dialogs.head.peer should_==(struct.Peer.group(respGroup.groupPeer.id))
          dialogs.head.message should_==(smsg)
        }
      }

      def changedTitle = {
        using(scope1) { implicit s =>
          sendMessage(scope2.user)
          Thread.sleep(100)

          editGroupTitle(respGroup.groupPeer, "New title")
          Thread.sleep(100)

          val smsg = ServiceMessage("Group title changed", Some(GroupChangedTitleExtension("New title")))

          val respHistory = loadHistory(respGroup.groupPeer.asOutPeer, 0l, 100)
          respHistory.history.length should_==(4)
          val msg = respHistory.history.head
          msg.senderUserId should_==(scope1.user.uid)
          msg.message should_==(smsg)

          val respDialogs = loadDialogs(0, 100)
          val dialogs = respDialogs.dialogs

          dialogs.length should_==(2)
          dialogs.head.peer should_==(struct.Peer.group(respGroup.groupPeer.id))
          dialogs.head.message should_==(smsg)
        }
      }

      def changedAvatar = {
        using(scope1) { implicit s =>
          sendMessage(scope2.user)
          Thread.sleep(100)

          val respAvatar = editGroupAvatar(respGroup.groupPeer, avatarFileLocation)
          Thread.sleep(100)

          val smsg = ServiceMessage("Group avatar changed", Some(GroupChangedAvatarExtension(Some(respAvatar.avatar))))

          val respHistory = loadHistory(respGroup.groupPeer.asOutPeer, 0l, 100)
          respHistory.history.length should_==(5)
          val msg = respHistory.history.head
          msg.senderUserId should_==(scope1.user.uid)
          msg.message should_==(smsg)

          val respDialogs = loadDialogs(0, 100)
          val dialogs = respDialogs.dialogs

          dialogs.length should_==(2)
          dialogs.head.peer should_==(struct.Peer.group(respGroup.groupPeer.id))
          dialogs.head.message should_==(smsg)
        }
      }

      def removedAvatar = {
        using(scope1) { implicit s =>
          sendMessage(scope2.user)
          Thread.sleep(100)

          removeGroupAvatar(respGroup.groupPeer)
          Thread.sleep(100)

          val smsg = ServiceMessage("Group avatar changed", Some(GroupChangedAvatarExtension(None)))

          val respHistory = loadHistory(respGroup.groupPeer.asOutPeer, 0l, 100)
          respHistory.history.length should_==(6)
          val msg = respHistory.history.head
          msg.senderUserId should_==(scope1.user.uid)
          msg.message should_==(smsg)

          val respDialogs = loadDialogs(0, 100)
          val dialogs = respDialogs.dialogs

          dialogs.length should_==(2)
          dialogs.head.peer should_==(struct.Peer.group(respGroup.groupPeer.id))
          dialogs.head.message should_==(smsg)
        }
      }

      def userLeft = {
        val smsg = ServiceMessage("User left the group", Some(UserLeftExtension()))

        using(scope2) { implicit s =>
          sendMessage(scope1.user)
          Thread.sleep(100)

          leaveGroup(respGroup.groupPeer)
          Thread.sleep(100)

          val respHistory = loadHistory(respGroup.groupPeer.asOutPeer, 0l, 100)
          respHistory.history.length should_==(7)
          val msg = respHistory.history.head
          msg.senderUserId should_==(scope2.user.uid)
          msg.message should_==(smsg)

          val respDialogs = loadDialogs(0, 100)
          val dialogs = respDialogs.dialogs

          dialogs.length should_==(2)
          dialogs.head.peer should_==(struct.Peer.privat(scope1.user.uid))

          dialogs.last.peer should_==(struct.Peer.group(respGroup.groupPeer.id))
          dialogs.last.message should_==(smsg)
        }

        using(scope1) { implicit s =>
          val respHistory = loadHistory(respGroup.groupPeer.asOutPeer, 0l, 100)
          respHistory.history.length should_==(7)
          val msg = respHistory.history.head
          msg.senderUserId should_==(scope2.user.uid)
          msg.message should_==(smsg)

          val respDialogs = loadDialogs(0, 100)
          val dialogs = respDialogs.dialogs

          dialogs.length should_==(2)
          dialogs.head.peer should_==(struct.Peer.privat(scope2.user.uid))

          dialogs.last.peer should_==(struct.Peer.group(respGroup.groupPeer.id))
          dialogs.last.message should_==(smsg)
        }
      }
    }
  }
}