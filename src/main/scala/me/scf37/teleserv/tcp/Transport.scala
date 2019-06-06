package me.scf37.teleserv.tcp

import scalaz.zio.IO
import scodec.bits.ByteVector

/**
  * ZIO-friendly async transport. We can read and write to it.
  */
trait Transport {
  def read(): IO[TransportError, ByteVector]
  def write(buf: ByteVector): IO[TransportError, Unit]
}
