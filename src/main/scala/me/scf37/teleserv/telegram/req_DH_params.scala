package me.scf37.teleserv.telegram

import javax.crypto.Cipher
import me.scf37.teleserv.proto.ProtocolState
import org.slf4j.LoggerFactory
import scalaz.zio.IO
import scalaz.zio.Task
import scalaz.zio.UIO
import scodec.Attempt
import scodec.Decoder
import scodec.Encoder
import scodec.bits.ByteVector

case class Server_DH_Params(
  nonce: Int128,
  server_nonce: Int128,
  p: TString,
  q: TString,
  public_key_fingerprint: Long,
  encrypted_data: TString
)

case class Req_DH_paramsState(
  nonce: Int128,
  serverNonce: Int128,
  p: TString,
  q: TString,
  pq: TString
)

case class P_Q_inner_data(
  pq: TString,
  p: TString,
  q: TString,
  nonce:Int128,
  server_nonce: Int128,
  new_nonce: Int256
)

object P_Q_inner_data {
  val codec = (TString.codec :: TString.codec :: TString.codec :: Int128.codec :: Int128.codec :: Int256.codec)
    .as[P_Q_inner_data]
}

/**
  * req_DH_params RPC call implementation
  */

case class req_DH_params(state: Req_DH_paramsState) extends ProtocolState {
  private val log = LoggerFactory.getLogger(getClass)

  override type Req = Server_DH_Params
  override type Rep = Boolean

  override val readCodec: Decoder[Req] =
    (
      Int128.codec ::
      Int128.codec ::
      TString.codec ::
      TString.codec ::
      scodec.codecs.long(64).withContext("public_key_fingerprint") ::
      TString.codec.withContext("encrypted_data")
    )
      .as[Server_DH_Params]

  override val writeCodec: Encoder[Rep] = scodec.codecs.bytes(0).contramap(_ => ByteVector.empty)

  override def handler(req: Req): Task[(Rep, Option[ProtocolState])] = (for {
    innerData <- decryptInnerData(req.encrypted_data.bytes)
    _ <- validateInnerData(innerData)
  } yield true -> None)
    .catchAll { err =>
      log.warn("failed to process req_DH_params.handler: " + err)
      UIO(false -> None)
    }

  private def validateInnerData(innerData: P_Q_inner_data): IO[String, Unit] =
    if (innerData.nonce != state.nonce) IO.fail("nonce mismatch")
    else if (innerData.server_nonce != state.serverNonce) IO.fail("server_nonce mismatch")
    else if (innerData.p != state.p) IO.fail("p mismatch")
    else if (innerData.q != state.q) IO.fail("q mismatch")
    else if (innerData.pq != state.pq) IO.fail("pq mismatch")
    else UIO(())

  private def decryptInnerData(data: ByteVector): IO[String, P_Q_inner_data] =
    UIO(P_Q_inner_data.codec.decode(decryptRsa(data, 255).bits)).flatMap {

      case Attempt.Successful(value) => UIO(value.value)

      case Attempt.Failure(cause) => IO.fail("decrypted body deserialization failed: " + cause.toString())
    }

  private def decryptRsa(data: ByteVector, dataSize: Long): ByteVector = {
    val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, TelegramRsa.key.privateKey)
    ByteVector(cipher.doFinal(data.toArray)).takeRight(dataSize)
  }
}
