package me.scf37.teleserv

import me.scf37.teleserv.tcp.TcpServer
import me.scf37.teleserv.telegram.TelegramProtocol
import org.slf4j.LoggerFactory
import scalaz.zio.UIO
import scalaz.zio.ZIO
import scalaz.zio.duration.Duration

object Main extends scalaz.zio.App {
  private val log = LoggerFactory.getLogger(getClass)

  override def run(args: List[String]): ZIO[Main.Environment, Nothing, Int] =
    for {
      _ <- TcpServer(7777, "localhost", TelegramProtocol).catchAll { e =>
        log.error("cannot start server", e)
        UIO(1)
      }
      clock <- ZIO.environment[Main.Environment].map(_.clock)
      _ <- clock.sleep(Duration.Infinity)
    } yield {
      0
    }

}
