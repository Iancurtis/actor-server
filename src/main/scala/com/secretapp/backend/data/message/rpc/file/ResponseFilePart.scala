package com.secretapp.backend.data.message.rpc.file

import com.secretapp.backend.data.message.rpc._
import scodec.bits.BitVector

case class ResponseFilePart(data: BitVector) extends RpcResponseMessage

object ResponseFilePart extends RpcResponseMessageObject {
  val responseType = 0x11
}