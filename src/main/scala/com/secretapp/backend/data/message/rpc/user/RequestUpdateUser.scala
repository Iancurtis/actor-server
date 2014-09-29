package com.secretapp.backend.data.message.rpc.user

import com.secretapp.backend.data.message.rpc._
import com.secretapp.backend.data.message.rpc.file.FileLocation

case class RequestUpdateUser(name: String) extends RpcRequestMessage {
  override val header = RequestUpdateUser.requestType
}

object RequestUpdateUser extends RpcRequestMessageObject {
  val requestType = 0x35
}