package me.scf37.teleserv.proto

import javax.crypto.Cipher
import me.scf37.teleserv.telegram.Int128
import me.scf37.teleserv.telegram.Req_DH_paramsState
import me.scf37.teleserv.telegram.ResPQ
import me.scf37.teleserv.telegram.TString
import me.scf37.teleserv.telegram.TelegramRsa
import me.scf37.teleserv.telegram.req_DH_params
import me.scf37.teleserv.telegram.req_pq
import org.scalatest.FreeSpec
import scalaz.zio.DefaultRuntime
import scalaz.zio.ZIO
import scodec.bits.BitVector
import scodec.bits.ByteVector

class TelegramProtoTest extends FreeSpec with DefaultRuntime {

  "req_pq" - {
    "response contains the same nonce" in {
      val req_pq_request =    """
        # nonce: int128
        00000000 00000000 00000000 00000001
      """
      val resp: ResPQ = handle(req_pq, bin(req_pq_request))

      assert(resp.nonce == Int128(bin("00000000 00000000 00000000 00000001").bytes))

    }
  }

  "req_DH_params" - {
    "RSA decryption works" in {
      val req_DH_params_request = """
        # nonce: int128
        00000000 00000000 00000000 00000001
        # server_nonce: int128
        00000000 00000000 00000000 00000002
        # p: TString
        010A0000
        # q: TString
        010B0000
        # public_key_fingerprint: Long

        # encrypted_data: TString
        """

      val p_q_inner_data = """
        # pq: TString
        01060000
        # p: TString
        01020000
        # q: TString
        01030000
        # nonce:Int128
        00000000 00000000 00000000 00000001
        # server_nonce: Int128
        00000000 00000000 00000000 00000002
        # new_nonce: Int256
        00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000003
        """

      val encrypted = encryptRsa(bin(p_q_inner_data).bytes.padTo(255))

      val req = bin(req_DH_params_request) ++
        scodec.codecs.long(64).encode(TelegramRsa.key.fingerprint).require ++
        TString.codec.encode(TString(encrypted)).require

      val state = Req_DH_paramsState(
        nonce = Int128(bin("00000000 00000000 00000000 00000001").bytes),
        serverNonce = Int128(bin("00000000 00000000 00000000 00000002").bytes),
        p = TString(bin("02").bytes),
        q = TString(bin("03").bytes),
        pq = TString(bin("06").bytes)
      )

      val resp = handle(req_DH_params(state), req)

      assert(resp == true)

    }
  }

  private def encryptRsa(data: ByteVector): ByteVector = {
    val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, TelegramRsa.key.publicKey)
    ByteVector(cipher.doFinal(data.toArray))
  }

  private def run[E, A](zio: ZIO[Any, E, A]): A =
    unsafeRunSync(zio)
      .fold(e => throw new RuntimeException(e.prettyPrint), identity)

  private def handle(state: ProtocolState, data: BitVector): state.Rep =
    run(state.handler(state.readCodec.decode(data).require.value))._1

  private def bin(s: String): BitVector =
    BitVector(
      s.split("\n").filterNot(_.trim.startsWith("#")).mkString("")
      .replaceAll("\\s", "")
        .grouped(2)
        .map(s => Integer.parseInt(s, 16).toByte).toArray)
}
