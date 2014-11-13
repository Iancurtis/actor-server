package com.secretapp.backend.data.message.update

import com.secretapp.backend.data.message.struct

@SerialVersionUID(1L)
case class MessageSent(peer: struct.Peer, randomId: Long, date: Long) extends SeqUpdateMessage {
  val header = MessageSent.header

  def userIds: Set[Int] = Set(peer.id)

  def groupIds: Set[Int] = peer.kind match {
    case struct.PeerType.Group =>
      Set(peer.id)
    case _ =>
      Set.empty
  }
}

object MessageSent extends SeqUpdateMessageObject {
  val header = 0x04
}
