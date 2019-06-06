package me.scf37.teleserv.tcp

/**
  * Error(s) thatn can be returned by transport layer
  * @param cause error cause, usually IOException.toString
  */
case class TransportError(cause: String)