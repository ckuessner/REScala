package replication

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import kofre.base.{Bottom, Id, Lattice}
import kofre.dotted.{Dotted, DottedLattice}
import kofre.syntax.{DeltaBuffer, Named}
import kofre.time.Dots
import loci.registry.{Binding, Registry}
import loci.serializer.jsoniterScala.given
import loci.transmitter.{IdenticallyTransmittable, RemoteRef, Transmittable}
import replication.JsoniterCodecs.given

import java.util.concurrent.atomic.AtomicReference
import scala.collection.View
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

type PushBinding[T] = Binding[T => Unit, T => Future[Unit]]

class DataManager[State: JsonValueCodec: DottedLattice: Bottom](
    replicaId: Id,
    registry: Registry
) {

  type TransferState = Named[Dotted[State]]


  // note that deltas are not guaranteed to be ordered the same in the buffers
  private val lock: AnyRef = new {}
  private var localDeltas: List[TransferState]  = Nil
  private var localBuffer: List[TransferState]  = Nil
  private var remoteDeltas: List[TransferState] = Nil

  def applyLocalDelta(dotted: Dotted[State]): Unit = lock.synchronized {
    val named = Named(replicaId, dotted)
    localBuffer = named :: localBuffer
  }

  def allDeltas: View[Named[Dotted[State]]] = lock.synchronized {
    View(localBuffer, remoteDeltas, localDeltas).flatten
  }

  def currentValue: Named[Dotted[State]] =
    Named(
      replicaId,
      allDeltas.map(_.anon).reduceOption(
        Lattice.merge[Dotted[State]]
      ).getOrElse(Bottom.empty)
    )

  def requestMissingBinding[PushBinding[Dots]] =
    given IdenticallyTransmittable[Dots] = IdenticallyTransmittable[Dots]()
    Binding[Dots => Unit]("requestMissing")

  val pushStateBinding: PushBinding[TransferState] =
    given JsonValueCodec[TransferState]           = JsonCodecMaker.make
    given IdenticallyTransmittable[TransferState] = IdenticallyTransmittable[TransferState]()
    Binding[TransferState => Unit]("pushState")

  registry.bind(pushStateBinding) { named =>
    lock.synchronized {
      remoteDeltas = named :: remoteDeltas
    }
  }

  registry.bindSbj(requestMissingBinding) { (rr: RemoteRef, knows: Dots) =>
    pushDeltas(allDeltas.filterNot(dt => dt.anon.context <= knows), rr)
  }

  def disseminate() =
    val deltas = lock.synchronized {
      val deltas = localBuffer
      localBuffer = Nil
      localDeltas = deltas concat localBuffer
      deltas
    }
    registry.remotes.foreach { remote =>
      pushDeltas(deltas.view, remote)
    }

  def requestMissing() =
    registry.remotes.headOption.foreach { rr =>
      val req = registry.lookup(requestMissingBinding, rr)
      req(currentValue.anon.context)
    }

  private def pushDeltas(deltas: View[TransferState], remote: RemoteRef): Unit = {
    val push = registry.lookup(pushStateBinding, remote)
    deltas.map(push).foreach(_.failed.foreach { cause =>
      println(s"sending to $remote failed: ${cause.toString}")
    })
  }
}
