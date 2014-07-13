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
import com.getsecretapp.{ proto => protobuf }

object NewYourDeviceCodec extends Codec[NewYourDevice] with utils.ProtobufCodec {
  def encode(n : NewYourDevice) = {
    val boxed = protobuf.UpdateNewYourDevice(n.uid, n.keyHash, n.key)
    encodeToBitVector(boxed)
  }

  def decode(buf : BitVector) = {
    decodeProtobuf(protobuf.UpdateNewYourDevice.parseFrom(buf.toByteArray)) {
      case Success(protobuf.UpdateNewYourDevice(uid, keyHash, key)) => NewYourDevice(uid, keyHash, key)
    }
  }
}
