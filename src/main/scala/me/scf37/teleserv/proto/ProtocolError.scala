package me.scf37.teleserv.proto

import scodec.Err

/**
  * Errors that can be returned by protocol
  */
sealed trait ProtocolError

object ProtocolError {
  /** failed to parse input bytes*/
  case class Parsing(err: Err) extends ProtocolError

  /** underlying transport failed */
  case class Transport(cause: String) extends ProtocolError

}

