package com.secretapp.backend.protocol.codecs.message.update

import com.secretapp.backend.protocol.codecs._
import com.secretapp.backend.data.message.update._
import com.secretapp.backend.protocol.codecs.utils.protobuf._
import scodec.bits._
import scodec.Codec
import scodec.codecs._
import scalaz._
import Scalaz._
import scala.util.Success
import im.actor.messenger.{ api => protobuf }

object UpdateSeqUpdateTooLongCodec extends Codec[UpdateSeqUpdateTooLong] with utils.ProtobufCodec {
  def encode(u: UpdateSeqUpdateTooLong) = {
    val boxed = protobuf.UpdateSeqUpdateTooLong()
    encodeToBitVector(boxed)
  }

  def decode(buf: BitVector) = {
    decodeProtobuf(protobuf.UpdateSeqUpdateTooLong.parseFrom(buf.toByteArray)) {
      case Success(protobuf.UpdateSeqUpdateTooLong()) => UpdateSeqUpdateTooLong()
    }
  }
}
