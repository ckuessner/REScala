package kofre.datatypes

import kofre.base.{Id, Lattice}
import kofre.time.WallClock

import java.time.Instant
import scala.math.Ordering.Implicits.infixOrderingOps

/** Lattice with the least-upper-bound defined by the timeStamp.
  * Timestamps must be unique, totally ordered, consistent with causal order.
  */
case class LastWriterWins[Time, Value](timestamp: Time, payload: Value)

object LastWriterWins {

  type TimedVal[Value] = LastWriterWins[WallClock, Value]

  def now[Value](payload: Value, replicaId: Id): LastWriterWins[WallClock, Value] =
    LastWriterWins(WallClock.now(replicaId), payload)

  implicit def Lattice[Time: Ordering, A]: Lattice[LastWriterWins[Time, A]] =
    new Lattice[LastWriterWins[Time, A]] {
      override def lteq(left: LastWriterWins[Time, A], right: LastWriterWins[Time, A]): Boolean =
        left.timestamp <= right.timestamp

      /** Decomposes a lattice state into its unique irredundant join decomposition of join-irreducible states */
      override def decompose(state: LastWriterWins[Time, A]): Iterable[LastWriterWins[Time, A]] = List(state)

      /** By assumption: associative, commutative, idempotent. */
      override def merge(left: LastWriterWins[Time, A], right: LastWriterWins[Time, A]): LastWriterWins[Time, A] =
        Ordering[Time].compare(left.timestamp, right.timestamp) match
          case 0 => if (left.payload == right.payload) then left
            else throw IllegalStateException(s"LWW same timestamp, different value: »$left«, »$right«")
          case -1 => right
          case 1  => left
    }
}
