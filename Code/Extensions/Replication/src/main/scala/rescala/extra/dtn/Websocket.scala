package rescala.extra.dtn

// to install scala-cli see https://scala-cli.virtuslab.org/

//> using scala "3.1.2"
//> using lib "com.softwaremill.sttp.client3::core::3.6.2"
//> using lib "com.softwaremill.sttp.client3::okhttp-backend::3.6.2"
//> using lib "com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-core::2.13.31"
//> using lib "com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros::2.13.31"

//> using repository "jitpack"
//> using lib "com.github.rescala-lang.rescala::kofre::6d9019e946"

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromArray, writeToArray}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.capabilities.WebSockets
import sttp.client3
import sttp.client3.{ws, *}
import sttp.model.Uri
import sttp.ws.{WebSocket, WebSocketFrame}
import sttp.client3.okhttp.OkHttpFutureBackend

import scala.concurrent.ExecutionContext.Implicits.global
import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.concurrent.{Await, Future}
import kofre.base.{Bottom, DecomposeLattice, Lattice}
import kofre.base.Defs.Id
import kofre.syntax.*
import kofre.datatypes.PosNegCounter

/** API base path used for http request */
val api: String =
  val ip   = "127.0.0.1"
  val port = 3000
  s"http://$ip:$port"

val backend: SttpBackend[Future, WebSockets] = OkHttpFutureBackend()

/** get uri body as string, throwing on any errors */
def sget(uri: Uri): Future[String]      = backend.send(basicRequest.get(uri).response(asStringAlways)).map(_.body)
def bget(uri: Uri): Future[Array[Byte]] = backend.send(basicRequest.get(uri).response(asByteArrayAlways)).map(_.body)

// what follows are the data type and codec definitions for receiving and sending bundles
case class WsRecvData(bid: String, src: String, dst: String, data: String):
  def payload: Array[Byte] = Base64.getDecoder.decode(data)
case class WsSendData(src: String, dst: String, data: String, delivery_notification: Boolean, lifetime: Long)
given JsonValueCodec[WsRecvData] = JsonCodecMaker.make
given JsonValueCodec[WsSendData] = JsonCodecMaker.make

given JsonValueCodec[PosNegCounter] = JsonCodecMaker.make

class Replica[S: Lattice](val id: String, val service: String, @volatile var data: S, sendDelta: S => Any) {
  def applyRemoteDelta(delta: S): Unit = synchronized {
    data = data merged delta
  }

  def applyLocalDelta(delta: S): Unit = synchronized {
    data = data merged delta
    sendDelta(data)
  }
}

given fullPermission[L: DecomposeLattice: Bottom]: PermIdMutate[Replica[L], L] = new {
  override def replicaId(c: Replica[L]): Id = c.id
  override def mutate(c: Replica[L], delta: L): Replica[L] =
    c.applyLocalDelta(delta)
    c
  override def query(c: Replica[L]): L = c.data
}
given [L]: ArdtOpsContains[Replica[L], L] = new {}

def handleStreamingReceive[T: JsonValueCodec](replica: Replica[T]): Future[Any] = {
  val uri = uri"$api/ws"
  val res = backend.send(basicRequest.get(uri).response(asWebSocketAlways { (ws: WebSocket[Future]) =>
    println(s"staring ws handler")
    // select json communication
    ws.sendText("/json")
    // ask to receive messages on the the given path
    ws.sendText(s"/subscribe ${replica.service}")

    def textReceiveLoop(): Future[Unit] = ws.receiveText().flatMap { text =>
      println(s"received $text")
      textReceiveLoop()
    }

    val txt = textReceiveLoop()

    def dataReceiveLoop(): Future[Unit] = ws.receiveBinary(true).flatMap { bin =>
      val receieved = readFromArray[WsRecvData](bin)
      println(s"received: $receieved")
      val delta = readFromArray[T](receieved.payload)
      println(s"applying $delta")
      replica.applyRemoteDelta(delta)
      println(s"value is now ${replica.data}")
      dataReceiveLoop()
    }

    val data = dataReceiveLoop()

    Future.traverse(Seq(txt, data))(identity)
  }))
  res.onComplete { res =>
    println(s"ws handler completed with: $res")
  }
  res
}

@main def run(): Unit =
  val service = "dtn://rdt/~test"

  def sendDelta[S: JsonValueCodec](delta: S): Future[String] =
    backend.send(
      basicRequest.post(uri"$api/send?dst=$service").body(writeToArray(delta)).response(asStringAlways)
      ).map { res =>
      println(s"sending delta: ${res.body}")
      res.body
    }

  val replica = Replica(kofre.base.Defs.genId(), service, PosNegCounter.zero, sendDelta)

  val fut =
    for
      _    <- sget(uri"$api/status/nodeid")
      _    <- sget(uri"$api/register?$service")
      info <- sget(uri"$api/status/bundles")
    yield
      val receiveFuture = handleStreamingReceive(replica)
      println(info)
      while
        val amount = scala.io.StdIn.readLine().toIntOption
        amount.foreach(replica.add)
        println(s"value is now ${replica.data.value}")
        amount.isDefined
      do ()
  println(Await.result(fut, scala.concurrent.duration.Duration.Inf))
