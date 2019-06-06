package me.scf37.teleserv.telegram

import org.scalatest.FreeSpec
import scodec.Codec
import scodec.bits.BitVector
import scodec.bits.ByteVector

class SerializationTest extends FreeSpec {
  "TString" - {
    "empty TString" in {
      val v = TString(ByteVector.empty)
      assert(TString.codec.encode(v).require == BitVector(0x00, 0x00, 0x00, 0x00))
      assertSame(v, TString.codec)
    }

    "small TString" in {
      val v1 = TString(ByteVector(0x10, 0x20))
      assert(TString.codec.encode(v1).require == BitVector(0x02, 0x10, 0x20, 0x00))
      assertSame(v1, TString.codec)

      val v2 = TString(ByteVector(0x10, 0x20, 0x30))
      assert(TString.codec.encode(v2).require == BitVector(0x03, 0x10, 0x20, 0x30))
      assertSame(v2, TString.codec)

      val v3 = TString(ByteVector(0x10, 0x20, 0x30, 0x40))
      assert(TString.codec.encode(v3).require == BitVector(0x04, 0x10, 0x20, 0x30, 0x40, 0x00, 0x00, 0x00))
      assertSame(v3, TString.codec)
    }

    "large TString" in {
      val v = TString(ByteVector.fill(401)(0xCC)) //401 = 0x191
      assert(TString.codec.encode(v).require.take(32) ==
        BitVector(0xFE, 0x91, 0x01, 0x00))

      assert(TString.codec.encode(v).require ==
        BitVector(0xFE, 0x91, 0x01, 0x00) ++ v.bytes.bits ++ ByteVector(0x00, 0x00, 0x00).bits)
      assertSame(v, TString.codec)
    }
  }

  private def assertSame[A](v: A, codec: Codec[A]) = {
    val r = codec.decode(codec.encode(v).require).require
    assert(r.value == v)
    assert(r.remainder.isEmpty)
  }

}
