package me.scf37.teleserv.tcp

import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

import me.scf37.teleserv.proto.Protocol
import me.scf37.teleserv.proto.ProtocolError
import org.slf4j.LoggerFactory
import scalaz.zio.DefaultRuntime
import scalaz.zio.Exit
import scalaz.zio.Ref
import scalaz.zio.Task
import scalaz.zio.UIO

import scala.util.Try

/**
  * TCP socket server, operating on specified protocol
  */
object TcpServer extends DefaultRuntime {
  private val log = LoggerFactory.getLogger(getClass)

  // default group is unbounded - creates too many threads under high load
  private lazy val limitedGroup = AsynchronousChannelGroup.withFixedThreadPool(16, new ThreadFactory {
    private val c = new AtomicInteger(1)
    override def newThread(r: Runnable): Thread = new Thread(r) {
      setName("nio2-" + c.getAndIncrement())
    }
  })

  /**
    * Create and start TCP server
    *
    * @param port port to bind to
    * @param iface interface to bind to
    * @param protocol protocol to handle traffic
    * @return AutoCloseable to close this server
    */
  def apply(
    port: Int,
    iface: String,
    protocol: Protocol): Task[AutoCloseable] = {

    for {
      ref <- Ref.make(new ConcurrentLinkedQueue[ByteBuffer])
      serverChannel <- Task {
        val ch = AsynchronousServerSocketChannel.open(limitedGroup)
        ch.bind(new InetSocketAddress(InetAddress.getByName(iface), port))
      }
    } yield {

      def accept(): Unit =
        serverChannel.accept((), new CompletionHandler[AsynchronousSocketChannel, Unit] {

          override def completed(channel: AsynchronousSocketChannel, attachment: Unit): Unit =
            try {
              accept()
              val transport = new AsyncSocketTransport(ref, channel)

              val zio = protocol.handle(transport).ensuring(UIO(channel.close())).catchAll {
                case ProtocolError.Parsing(err) =>
                  log.info("Parsing failure: " + err.message)
                  UIO(())
                case ProtocolError.Transport(cause) =>
                  log.info("Transport failure: " + cause)
                  UIO(())
              }

              unsafeRunAsync(zio) {
                case Exit.Success(_) => ()
                case Exit.Failure(cause) =>
                  log.error(cause.prettyPrint)
              }

            } catch {
              case e: Throwable =>
                Try(channel.close())
                log.info(s"accept $iface:$port failed", e)
            }

          override def failed(exc: Throwable, attachment: Unit): Unit = {
            log.info(s"accept $iface:$port failed: " + exc.toString)
          }
        })

      accept()

      serverChannel
    }
  }
}