package com.secretapp.backend.protocol.codecs.message.update

import scala.util.{ Try, Success, Failure }
import com.secretapp.backend.data.message.update._
import scodec.bits.BitVector
import scalaz._
import Scalaz._

object SeqUpdateMessageCodec {
  def encode(body: SeqUpdateMessage): String \/ BitVector = {
    body match {
      case m: Message           => MessageCodec.encode(m)
      case m: MessageSent       => MessageSentCodec.encode(m)
      case n: NewDevice         => NewDeviceCodec.encode(n)
      case n: NewYourDevice     => NewYourDeviceCodec.encode(n)
      case u: AvatarChanged     => AvatarChangedCodec.encode(u)
      case u: ContactRegistered => ContactRegisteredCodec.encode(u)
      case u: MessageReceived   => MessageReceivedCodec.encode(u)
      case u: MessageRead       => MessageReadCodec.encode(u)
      case u: GroupInvite       => GroupInviteCodec.encode(u)
      case u: GroupMessage      => GroupMessageCodec.encode(u)
      case u: GroupUserAdded    => GroupUserAddedCodec.encode(u)
      case u: GroupUserLeave    => GroupUserLeaveCodec.encode(u)
      case u: GroupUserKick     => GroupUserKickCodec.encode(u)
      case u: GroupCreated      => GroupCreatedCodec.encode(u)
    }
  }

  def decode(commonUpdateHeader: Int, buf: BitVector): String \/ SeqUpdateMessage = {
    val tried = Try(commonUpdateHeader match {
      case Message.seqUpdateHeader           => MessageCodec.decode(buf)
      case MessageSent.seqUpdateHeader       => MessageSentCodec.decode(buf)
      case NewDevice.seqUpdateHeader         => NewDeviceCodec.decode(buf)
      case NewYourDevice.seqUpdateHeader     => NewYourDeviceCodec.decode(buf)
      case AvatarChanged.seqUpdateHeader     => AvatarChangedCodec.decode(buf)
      case ContactRegistered.seqUpdateHeader => ContactRegisteredCodec.decode(buf)
      case MessageReceived.seqUpdateHeader   => MessageReceivedCodec.decode(buf)
      case MessageRead.seqUpdateHeader       => MessageReadCodec.decode(buf)
      case GroupInvite.seqUpdateHeader       => GroupInviteCodec.decode(buf)
      case GroupMessage.seqUpdateHeader      => GroupMessageCodec.decode(buf)
      case GroupUserAdded.seqUpdateHeader    => GroupUserAddedCodec.decode(buf)
      case GroupUserLeave.seqUpdateHeader    => GroupUserLeaveCodec.decode(buf)
      case GroupUserKick.seqUpdateHeader     => GroupUserKickCodec.decode(buf)
      case GroupCreated.seqUpdateHeader      => GroupCreatedCodec.decode(buf)
    })
    tried match {
      case Success(res) => res match {
        case \/-(r) => r._2.right
        case l@(-\/(_)) => l
      }
      case Failure(e) => e.getMessage.left
    }
  }
}
