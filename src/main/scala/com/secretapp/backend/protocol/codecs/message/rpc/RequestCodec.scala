package com.secretapp.backend.protocol.codecs.message.rpc

import com.secretapp.backend.protocol.codecs._
import com.secretapp.backend.protocol.codecs.message.rpc.auth._
import com.secretapp.backend.protocol.codecs.message.rpc.update._
import com.secretapp.backend.protocol.codecs.message.rpc.messaging._
import com.secretapp.backend.data.message.rpc._
import com.secretapp.backend.data.message.rpc.auth._
import com.secretapp.backend.data.message.rpc.update._
import com.secretapp.backend.data.message.rpc.messaging._
import scodec.bits._
import scodec.Codec
import scodec.codecs._

object RequestCodec extends Codec[Request] {
  val rpcRequestMessageCodec: Codec[RpcRequestMessage] = discriminated[RpcRequestMessage].by(uint32)
    .\(RequestGetDifference.requestType) { case r: RequestGetDifference => r } (protoPayload(RequestGetDifferenceCodec))
    .\(RequestGetState.requestType) { case r: RequestGetState => r } (protoPayload(RequestGetStateCodec))
    .\(RequestAuthCode.requestType) { case r: RequestAuthCode => r } (protoPayload(RequestAuthCodeCodec))
    .\(RequestSignIn.requestType) { case r: RequestSignIn => r } (protoPayload(RequestSignInCodec))
    .\(RequestSignUp.requestType) { case r: RequestSignUp => r } (protoPayload(RequestSignUpCodec))
    .\(RequestSendMessage.requestType) { case r: RequestSendMessage => r } (protoPayload(RequestSendMessageCodec))

  private val codec = rpcRequestMessageCodec.pxmap[Request](Request.apply, Request.unapply)

  def encode(r: Request) = codec.encode(r)

  def decode(buf: BitVector) = codec.decode(buf)
}
