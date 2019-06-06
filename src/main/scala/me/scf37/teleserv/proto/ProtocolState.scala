package me.scf37.teleserv.proto

import scalaz.zio.Task
import scodec.Decoder
import scodec.Encoder

/**
  * ProtocolState - defines current connection state and what to do next
  */
trait ProtocolState {
  type Req
  type Rep

  def readCodec: Decoder[Req]
  def writeCodec: Encoder[Rep]

  def handler(req: Req): Task[(Rep, Option[ProtocolState])]
}

