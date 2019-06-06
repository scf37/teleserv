package me.scf37.teleserv.proto.echo

import me.scf37.teleserv.proto.Protocol
import me.scf37.teleserv.proto.ProtocolState
import scalaz.zio.Task
import scodec.Attempt
import scodec.Codec
import scodec.DecodeResult
import scodec.Decoder
import scodec.Encoder
import scodec.Err
import scodec.SizeBound
import scodec.bits.BitVector

object EchoProtocol extends Protocol {

  private case class HelloRequest(s: String)

  private case class HelloResponse(s: String)

  private case class HelloProtocol2State(s1: String)

  private object Codecs {
    val lineCodec: Codec[String] = new Codec[String] {

      import scodec.codecs._

      private val eol = BitVector.fromByte(0x0a.toByte)

      override def encode(value: String): Attempt[BitVector] =
        utf8.encode(value).map(_ ++ eol)

      override def sizeBound: SizeBound = SizeBound.atLeast(1)

      override def decode(bits: BitVector): Attempt[DecodeResult[String]] =
        bits.bytes.indexOfSlice(eol.bytes) match {
          case -1 => Attempt.failure(Err.insufficientBits(-1, bits.size))
          case i =>
            val j = if (i > 0 && bits.getByte(i - 1) == 0x0d) i - 1 else i
            utf8.decode(bits.take(j * 8)).map(_.copy(remainder = bits.drop(j * 8 + 8)))
        }
    }
  }

  private case object HelloProtocol1 extends ProtocolState {
    override type Req = HelloRequest
    override type Rep = HelloResponse

    override def readCodec: Decoder[HelloRequest] = Codecs.lineCodec.map(HelloRequest.apply)

    override def writeCodec: Encoder[HelloResponse] = Codecs.lineCodec.contramap(_.s)

    override def handler(req: HelloRequest): Task[(HelloResponse, Option[HelloProtocol2])] = {

      Task {
        if (req.s.isEmpty)
          HelloResponse("Bye!") -> None
        else
          HelloResponse("OK") -> Option(HelloProtocol2(HelloProtocol2State(req.s)))
      }
    }
  }

  private case class HelloProtocol2(state: HelloProtocol2State) extends ProtocolState {
    override type Req = HelloRequest
    override type Rep = HelloResponse

    override def readCodec: Decoder[HelloRequest] = Codecs.lineCodec.map(HelloRequest.apply)

    override def writeCodec: Encoder[HelloResponse] = Codecs.lineCodec.contramap(_.s)

    override def handler(req: HelloRequest): Task[(HelloResponse, Option[HelloProtocol1.type])] = {
      Task {
        if (req.s.isEmpty)
          HelloResponse("Bye!") -> None
        else
          HelloResponse(state.s1 + ", " + req.s + "!") -> Option(HelloProtocol1)
      }
    }
  }

  override val firstState: ProtocolState = HelloProtocol1
}