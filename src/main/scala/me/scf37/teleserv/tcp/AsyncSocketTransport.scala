package me.scf37.teleserv.tcp

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.ConcurrentLinkedQueue

import scalaz.zio.IO
import scalaz.zio.Ref
import scalaz.zio.UIO
import scalaz.zio.ZIO
import scodec.bits.ByteVector

/**
  * Transport implementation using NIO.2 AsynchronousSocketChannel
  *
  * @param buffers Ref for native ByteBuffer cache to reuse them
  * @param channel channel to work with
  */
class AsyncSocketTransport(
  buffers: Ref[ConcurrentLinkedQueue[ByteBuffer]],
  channel: AsynchronousSocketChannel
) extends Transport {

  def read(): IO[TransportError, ByteVector] = withBuffer { buf =>
    IO.effectAsync { cb =>

      channel.read(buf, (), new CompletionHandler[java.lang.Integer, Unit] {

        override def completed(result: java.lang.Integer, attachment: Unit): Unit = {
          buf.flip()
          cb(UIO(ByteVector(buf)))
        }

        override def failed(exc: Throwable, attachment: Unit): Unit =
          cb(handleTransportException(exc))

      })
    }
  }

  def write(buf: ByteVector): IO[TransportError, Unit] = {
    val byteBuf = buf.toByteBuffer

    // channel write operation may accept less bytes than passed in.
    def loop(): IO[TransportError, Unit] = doWrite(byteBuf).flatMap { remaining =>
      if (remaining > 0) loop() else UIO(())
    }

    loop()
  }


  private def doWrite(buf: ByteBuffer): IO[TransportError, Int] =
    IO.effectAsync { cb =>
      channel.write(buf, (), new CompletionHandler[java.lang.Integer, Unit] {

        override def completed(result: java.lang.Integer, attachment: Unit): Unit =
          cb(UIO(result))

        override def failed(exc: Throwable, attachment: Unit): Unit =
          cb(handleTransportException(exc))
      })
    }

  private def handleTransportException[A](e: Throwable): IO[TransportError, A] = e match {
    case e: IOException => IO.fail(TransportError(e.toString))
    case e => IO.die(e)
  }

  private def withBuffer[R, E, A](f: ByteBuffer => ZIO[R, E, A]): ZIO[R, E, A] = {
    buffers.get.map { q =>
      q -> Option(q.poll()).getOrElse(ByteBuffer.allocateDirect(4096))
    }.bracket { case (q, buf) => UIO(q.add(buf)) } { case (_, buf) =>
      buf.clear()
      f(buf)
    }
  }

}
