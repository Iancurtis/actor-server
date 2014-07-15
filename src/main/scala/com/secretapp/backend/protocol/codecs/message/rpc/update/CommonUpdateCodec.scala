package com.secretapp.backend.protocol.codecs.message.rpc.update

import com.secretapp.backend.protocol.codecs._
import com.secretapp.backend.data.message.rpc.update._
import com.secretapp.backend.data.message.update._
import com.secretapp.backend.data.message.rpc._
import com.secretapp.backend.protocol.codecs.message.UpdateMessageCodec
import com.secretapp.backend.protocol.codecs.message.update._
import com.secretapp.backend.protocol.codecs.utils.protobuf._
import scodec.bits._
import scodec.Codec
import scodec.codecs._
import scalaz._
import Scalaz._
import scala.util.{ Try, Failure, Success }
import com.getsecretapp.{ proto => protobuf }

object CommonUpdateCodec extends Codec[CommonUpdate] with utils.ProtobufCodec {
  def encode(u : CommonUpdate) = {
    u.toProto match {
      case \/-(boxed) => encodeToBitVector(boxed)
      case l@(-\/(_)) => l
    }
  }

  def decode(buf : BitVector) = {
    Try(protobuf.CommonUpdate.parseFrom(buf.toByteArray)) match {
      case Success(u@protobuf.CommonUpdate(_, _, _, _)) =>
        CommonUpdate.fromProto(u) match {
          case \/-(unboxed) => (BitVector.empty, unboxed).right
          case l@(-\/(_)) => l
        }
      case Failure(e) => s"parse error: ${e.getMessage}".left
    }
  }
}
