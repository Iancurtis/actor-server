package com.secretapp.backend.protocol.codecs.message.update

import com.secretapp.backend.protocol.codecs._
import com.secretapp.backend.data.message.rpc.update._
import com.secretapp.backend.data.message.update._
import com.secretapp.backend.data.message.rpc._
import com.secretapp.backend.protocol.codecs.message.update._
import com.secretapp.backend.protocol.codecs.utils.protobuf._
import scodec.bits._
import scodec.Codec
import scodec.codecs._
import scalaz._
import Scalaz._
import scala.util.{ Try, Failure, Success }
import im.actor.messenger.{ api => protobuf }

object FatSeqUpdateCodec extends Codec[FatSeqUpdate] with utils.ProtobufCodec {
  def encode(u: FatSeqUpdate) = {
    u.toProto match {
      case \/-(boxed) => encodeToBitVector(boxed)
      case l@(-\/(_)) => l
    }
  }

  def decode(buf: BitVector) = {
    decodeProtobufEither(protobuf.UpdateFatSeqUpdate.parseFrom(buf.toByteArray)) {
      case Success(u: protobuf.UpdateFatSeqUpdate) =>
        FatSeqUpdate.fromProto(u) match {
          case \/-(unboxed) => unboxed.right
          case l@(-\/(_)) => l
        }
    }
  }
}
