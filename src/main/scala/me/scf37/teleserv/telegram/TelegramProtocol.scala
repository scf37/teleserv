package me.scf37.teleserv.telegram

import me.scf37.teleserv.proto.Protocol
import me.scf37.teleserv.proto.ProtocolState

/**
  * Telegram protocol, supporting only two commands for now
  */
object TelegramProtocol extends Protocol {
  override val firstState: ProtocolState = req_pq
}
