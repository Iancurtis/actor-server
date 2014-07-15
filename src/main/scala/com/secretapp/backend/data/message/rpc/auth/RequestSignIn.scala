package com.secretapp.backend.data.message.rpc.auth

import com.secretapp.backend.data.message.rpc._
import scodec.bits.BitVector

case class RequestSignIn(phoneNumber : Long,
                         smsHash : String,
                         smsCode : String,
                         publicKey : BitVector) extends RpcRequestMessage
object RequestSignIn extends RpcRequestMessageObject {
  val requestType = 0x3
}