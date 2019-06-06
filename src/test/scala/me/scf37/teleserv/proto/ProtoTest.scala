package me.scf37.teleserv.proto

import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

import me.scf37.teleserv.proto.echo.EchoProtocol
import me.scf37.teleserv.tcp.TcpServer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FreeSpec
import scalaz.zio.DefaultRuntime

import scala.collection.mutable
import scala.util.Try

class ProtoTest extends FreeSpec with BeforeAndAfterAll with DefaultRuntime {
  val port = {
    val ss = new ServerSocket(0)
    val port = ss.getLocalPort
    ss.close()
    port
  }
  val server = TcpServer(port, "localhost", EchoProtocol)
  val closeHandle = unsafeRunSync(server).fold(err => throw new RuntimeException(err.prettyPrint), identity)

  override protected def afterAll() = closeHandle.close()

  "simple two step protocol works" in withClient {c =>
    c.write("Hello\n")
    assert(c.read() == "OK")
    c.write("World\n")
    assert(c.read() == "Hello, World!")
  }

  "simple two step protocol works twice on the same connection" in withClient {c =>
    c.write("Hello\n")
    assert(c.read() == "OK")
    c.write("World\n")
    assert(c.read() == "Hello, World!")

    c.write("Goodbye\n")
    assert(c.read() == "OK")
    c.write("Java\n")
    assert(c.read() == "Goodbye, Java!")
  }

  "chunked data support" in withClient {c =>
    c.write("Hel")
    Thread.sleep(200)
    c.write("lo\n")
    assert(c.read() == "OK")
    c.write("Worl")
    Thread.sleep(200)
    c.write("d\n")
    assert(c.read() == "Hello, World!")
  }

  private def withClient(f: Client => Unit): Unit = {
    val c = new Client
    try {
      f(c)
    } finally {
      c.close()
    }
  }

  private class Client {
    val socket = new Socket("localhost", port)
    val is = socket.getInputStream
    val os = socket.getOutputStream
    def write(s: String): Unit = os.write(s.getBytes(StandardCharsets.UTF_8))
    def read(): String = {
      val r = mutable.ArrayBuffer.empty[Byte]
      while (true) {
        val b = is.read()
        if (b == 0x0a) return new String(r.toArray, StandardCharsets.UTF_8)
        r += b.toByte
      }
      ???
    }
    def close(): Unit = Try(socket.close())
  }
}
