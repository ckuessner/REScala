package rescala.operator

import scala.annotation.implicitNotFound
import scala.reflect.ClassTag
import scala.collection.IterableOps
import rescala.operator.SignalApi
import rescala.core.Core
import rescala.interface.RescalaInterface

trait FlattenApi {
  self : RescalaInterface =>
  @implicitNotFound(msg =
    "Could not flatten ${A}. Try to select a specific flatten strategy from rescala.reactives.Flatten.")
  trait Flatten[-A, R] {
    def apply(sig: A): R
  }
  object Flatten {

    /** Flatten a Signal[Signal[B]\] into a Signal[B] that changes whenever the outer or inner signal changes. */
    implicit def signal[B](implicit
        ticket: CreationTicket
    ): Flatten[Signal[Signal[B]], Signal[B]] =
      new Flatten[Signal[Signal[B]], Signal[B]] {
        def apply(sig: Signal[Signal[B]]): Signal[B] =
          Signals.dynamic(sig) { t => t.depend(t.depend(sig).resource) }
      }

    /** Flatten a Signal[Array[Signal[B]\]\] into a Signal[Array[B]\] where the new Signal updates whenever any of the inner or the outer signal updates */
    implicit def arraySignals[B: ClassTag, Sig[U] <: Signal[U]](implicit
        ticket: CreationTicket
    ): Flatten[Signal[Array[Sig[B]]], Signal[Array[B]]] =
      new Flatten[Signal[Array[Sig[B]]], Signal[Array[B]]] {
        def apply(sig: Signal[Array[Sig[B]]]): Signal[Array[B]] =
          Signals.dynamic(sig) { t => t.depend(sig) map { (r: Signal[B]) => t.depend(r) } }
      }

    /** Flatten a Signal[Option[Signal[B]\]\] into a Signal[Option[B]\] where the new Signal updates whenever any of the inner or the outer signal updates */
    implicit def optionSignal[B, Sig[U] <: Signal[U]](implicit
        ticket: CreationTicket
    ): Flatten[Signal[Option[Sig[B]]], Signal[Option[B]]] =
      new Flatten[Signal[Option[Sig[B]]], Signal[Option[B]]] {
        def apply(sig: Signal[Option[Sig[B]]]): Signal[Option[B]] =
          Signals.dynamic(sig) { t => t.depend(sig) map { (r: Signal[B]) => t.depend(r) } }
      }

    /** Flatten a Signal[Event[B]]\] into a Event[B] where the new Event fires whenever the current inner event fires */
    implicit def event[A, B, Evnt[A1] <: Event[A1]](implicit
        ticket: CreationTicket,
    ): Flatten[Signal[Evnt[B]], Event[B]] =
      new Flatten[Signal[Evnt[B]], Event[B]] {
        def apply(sig: Signal[Evnt[B]]): Event[B] = Events.dynamic(sig) { t => t.depend(t.depend(sig)) }
      }

    /** Flatten a Event[Option[B]\] into a Event[B] that fires whenever the inner option is defined. */
    implicit def option[A, B](implicit
        ticket: CreationTicket
    ): Flatten[Event[Option[B]], Event[B]] =
      new Flatten[Event[Option[B]], Event[B]] {
        def apply(event: Event[Option[B]]): Event[B] =
          Events.static(event) { t => t.dependStatic(event).flatten }
      }

  }


  /** Flatten a Signal[Traversable[Signal[B]\]\] into a Signal[Traversable[B]\] where the new Signal updates whenever any of the inner or the outer signal updates */
  implicit def traversableSignals[B, T[U] <: IterableOps[U, T, T[U]], Sig[A1] <: Signal[A1]](implicit
      ticket: CreationTicket
  ): Flatten[Signal[T[Sig[B]]], Signal[T[B]]] =
    new Flatten[Signal[T[Sig[B]]], Signal[T[B]]] {
      def apply(sig: Signal[T[Sig[B]]]): Signal[T[B]] =
        Signals.dynamic(sig) { t => t.depend(sig).map { (r: Signal[B]) => t.depend(r) } }
    }

  /** Flatten a Signal[Traversable[Event[B]\]\] into a Event[B]. The new Event fires the value of any inner firing Event.
    * If multiple inner Events fire, the first one in iteration order is selected.
    */
  def firstFiringEvent[B, T[U] <: IterableOps[U, T, T[U]], Evnt[A1] <: Event[A1]](
      implicit
      ticket: CreationTicket
  ): Flatten[Signal[T[Evnt[B]]], Event[B]] =
    new Flatten[Signal[T[Evnt[B]]], Event[B]] {
      def apply(sig: Signal[T[Evnt[B]]]): Event[B] =
        Events.dynamic(sig) { t =>
          val all = t.depend(sig) map { (r: Event[B]) => t.depend(r) }
          all.collectFirst { case Some(e) => e }
        }
    }

  /** Flatten a Signal[Traversable[Event[B]\]\] into a Event[Traversable[Option[B]\]\] where the new Event fires whenever any of the inner events fire */
  def traversableOfAllOccuringEventValues[B, T[U] <: IterableOps[U, T, T[U]], Evnt[A1] <: Event[A1]](implicit
      ticket: CreationTicket
  ): Flatten[Signal[T[Evnt[B]]], Event[T[Option[B]]]] =
    new Flatten[Signal[T[Evnt[B]]], Event[T[Option[B]]]] {
      def apply(sig: Signal[T[Evnt[B]]]): Event[T[Option[B]]] =
        Events.dynamic(sig) { t =>
          val all = t.depend(sig) map { (r: Event[B]) => t.depend(r) }
          if (all.exists(_.isDefined)) Some(all) else None
        }
    }

}
