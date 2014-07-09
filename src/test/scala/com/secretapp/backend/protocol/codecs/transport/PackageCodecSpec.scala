package com.secretapp.backend.protocol.codecs.transport

import com.secretapp.backend.protocol.codecs._
import com.secretapp.backend.data._
import scodec.bits._
import org.scalatest._
import scalaz._
import Scalaz._

class PackageCodecSpec extends FlatSpec with Matchers {
  "encode" should "pack Package" in {
    protoPackage.build(1L, 2L, 3L, RequestAuthId()) should === {
      hex"00000000000000010000000000000002000000000000000301f0".bits.right
    }
  }

  "decode" should "unpack bytes to Package" in {
    val v = hex"00000000000000010000000000000002000000000000000301f0".bits
    val p = Package(1L, 2L, ProtoMessageWrapper(3L, RequestAuthId()))
    protoPackage.decode(v) should === ((BitVector.empty, p).right)
  }

}