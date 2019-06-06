package me.scf37.teleserv.proto

import me.scf37.teleserv.tcp.Transport
import scalaz.zio.IO
import scalaz.zio.UIO
import scodec.Attempt
import scodec.DecodeResult
import scodec.Err.InsufficientBits
import scodec.bits.ByteVector

/**
  * Protocol is an FSM where set of ProtocolState instances represent states
  * Current ProtocolState defines how to parse request, how to serialize response and
  *   what the next state is
  *
  * Logic is as follows:
  * - read and deserialize request using current state codecs
  * - handle deserialized request via current state
  * - serialize and write response returned from handler
  * - if handler returned next state, switch to it. otherwise close the connection
  *
  * Due to usage of monadic recursion, Ref is not needed here to hold state.
  */
abstract class Protocol {
  /**
    * Initial state that parses first request
    */
  val firstState: ProtocolState

  /**
    * Reads requests and write responses to this transport, defined by used protocol
    *
    * @param transport transport
    * @return IO which completes when protocol shuts down the connection
    */
  def handle(transport: Transport): IO[ProtocolError, Unit] = {

    // read data from transport until request type is parsed
    def read(vector: ByteVector, ps: ProtocolState): IO[ProtocolError, DecodeResult[ps.Req]] = (for {
      bytes <- transport.read().map(vector ++ _).mapError(e => ProtocolError.Transport(e.cause))
      // = is intentional - tailing map in recursive for-comprehension leaks memory
      result = ps.readCodec.decode(bytes.bits) match {
        case Attempt.Successful(value) => UIO(value)
        case Attempt.Failure(InsufficientBits(_, _, _)) => read(bytes, ps)
        case Attempt.Failure(cause) => IO.fail(ProtocolError.Parsing(cause))
      }
    } yield result).flatten

    def loop(vector: ByteVector, state: ProtocolState): IO[ProtocolError, Unit] = {
      (for {
        req <- read(ByteVector.empty, state)
        respTuple <- state.handler(req.value).flatMapError(e => IO.die(e))
        (rep, nextState) = respTuple
        outBytes <- state.writeCodec.encode(rep) match {
          case Attempt.Successful(value) => UIO(value)
          case Attempt.Failure(cause) => IO.fail(ProtocolError.Parsing(cause))
        }
        _ <- transport.write(outBytes.bytes).mapError(e => ProtocolError.Transport(e.cause))
        // = is intentional - tailing map in recursive for-comprehension leaks memory
        next = nextState.fold[IO[ProtocolError, Unit]](UIO(()))(st => loop(req.remainder.bytes, st))
      } yield next).flatten
    }

    loop(ByteVector.empty, firstState)

  }
}
