package com.secretapp.backend.data.message.rpc.presence

import com.secretapp.backend.data.message.rpc._
import com.secretapp.backend.data.message.struct.UserId
import scala.collection.immutable

case class UnsubscribeFromOnline(users: immutable.Seq[UserId]) extends RpcRequestMessage {
  val header = UnsubscribeFromOnline.requestType
}

object UnsubscribeFromOnline extends RpcRequestMessageObject {
  val requestType = 0x21
}