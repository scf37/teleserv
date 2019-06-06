package me.scf37.teleserv.telegram

import scodec.Codec
import scodec.bits.ByteVector

case class Int128(bytes: ByteVector)
object Int128 {
  val codec: Codec[Int128] =
    scodec.codecs.bits(128)
      .xmap[Int128](bits => Int128(bits.bytes), _.bytes.bits)
}

case class Int256(bytes: ByteVector)
object Int256 {
  val codec: Codec[Int256] =
    scodec.codecs.bits(256)
      .xmap[Int256](bits => Int256(bits.bytes), _.bytes.bits)
}

case class TString(bytes: ByteVector)

object TString {
  import scodec.codecs._
  val codec: Codec[TString] = {
    uint(8)
      .flatZip {
        case 0xFE | 0xFF => int24L
        case l => provide(l & 0xFF)
      }
      .xmap[Int](
      _._2,
      l =>
        if (l < 254) l -> l else 0xFE -> l)
      .flatZip { len =>
        bytes(if (len < 254) (len + 1 + 3) / 4 * 4 - 1 else (len + 3) / 4 * 4)
          .xmap[ByteVector](_.take(len.toLong), v => v)
      }.xmap[TString](
        v => TString(v._2),
        v => v.bytes.length.toInt -> v.bytes
      )
  }
}

case class TLongVector(values: Seq[Long])
object TLongVector {
  import scodec.codecs._
  val codec: Codec[TLongVector] = constant(0x1cb5c415)
    .dropLeft(vectorOfN(int32, long(64)))
    .xmap(TLongVector.apply, _.values.toVector)
}