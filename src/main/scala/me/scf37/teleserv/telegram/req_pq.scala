package me.scf37.teleserv.telegram

import java.security.SecureRandom

import me.scf37.teleserv.proto.ProtocolState
import scalaz.zio.Task
import scodec.Decoder
import scodec.Encoder
import scodec.bits.ByteVector

case class ReqPQ(nonce: Int128)
case class ResPQ(
  nonce: Int128,
  server_nonce: Int128,
  pq: TString,
  server_public_key_fingerprints: TLongVector)

/**
  * req_pq RPC call implementation
  */
case object req_pq extends ProtocolState {
  override type Req = ReqPQ
  override type Rep = ResPQ

  override val readCodec: Decoder[Req] =
    Int128.codec.as[ReqPQ]

  override val writeCodec: Encoder[Rep] =
    (Int128.codec :: Int128.codec :: TString.codec :: TLongVector.codec).as[ResPQ]

  private val random = new SecureRandom()
  private def randomVector(bytes: Int): ByteVector = {
    val buf = new Array[Byte](bytes)
    random.nextBytes(buf)
    ByteVector(buf)
  }

  override def handler(req: Req): Task[(Rep, Option[ProtocolState])] = Task {
    val serverNonce: Int128 = Int128(randomVector(128 / 8))

    val rep = ResPQ(
      nonce = req.nonce,
      server_nonce = serverNonce,
      pq = TString(randomVector(64)),
      server_public_key_fingerprints = TLongVector(Seq(TelegramRsa.key.fingerprint))
    )

    rep -> Some(req_DH_params(Req_DH_paramsState(
      nonce = req.nonce,
      serverNonce = serverNonce,
      pq = rep.pq,
      p =  TString(randomVector(32)),
      q = TString(randomVector(32)),
    )))
  }
}
