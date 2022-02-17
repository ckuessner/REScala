package kofre.decompose.interfaces

import kofre.causality.{CContext, Dot, Causal}
import kofre.decompose.*
import kofre.decompose.CRDTInterface.{DeltaMutator, DeltaQuery}
import kofre.decompose.DotStore.*

object AWSetInterface {
  type State[E] = Causal[DotMap[E, DotSet]]

  trait AWSetCompanion {
    type State[E] = AWSetInterface.State[E]
    type Embedded[E] = DotMap[E, DotSet]
  }

  private class DeltaStateFactory[E] {
    val bottom: State[E] = UIJDLattice[State[E]].bottom

    def make(
        dm: DotMap[E, DotSet] = bottom.dotStore,
        cc: C = bottom.cc
    ): State[E] = Causal(dm, cc)
  }

  private def deltaState[E]: DeltaStateFactory[E] = new DeltaStateFactory[E]

  def elements[E]: DeltaQuery[State[E], Set[E]] = {
    case Causal(dm, _) => dm.keySet
  }

  def add[E](e: E): DeltaMutator[State[E]] = {
    case (replicaID, Causal(dm, cc)) =>
      val nextDot = CContext[C].nextDot(cc, replicaID)
      val v       = dm.getOrElse(e, DotSet.empty)

      deltaState[E].make(
        dm = DotMap[E, DotSet].empty.updated(e, Set(nextDot)),
        cc = CContext[C].fromSet(v + nextDot)
      )
  }

  def addAll[E](elems: Iterable[E]): DeltaMutator[State[E]] = {
    case (replicaID, Causal(dm, cc)) =>
      val nextCounter = CContext[C].nextDot(cc, replicaID).time
      val nextDots    = (nextCounter until nextCounter + elems.size).toSet.map(Dot(replicaID, _))

      val ccontextSet = elems.foldLeft(nextDots) {
        case (dots, e) => dm.get(e) match {
            case Some(ds) => dots union ds
            case None     => dots
          }
      }

      deltaState[E].make(
        dm = (elems zip nextDots.map(Set(_))).toMap,
        cc = CContext[C].fromSet(ccontextSet)
      )
  }

  def remove[E](e: E): DeltaMutator[State[E]] = {
    case (_, Causal(dm, _)) =>
      val v = dm.getOrElse(e, DotSet.empty)

      deltaState[E].make(
        cc = CContext[C].fromSet(v)
      )
  }

  def removeAll[E](elems: Iterable[E]): DeltaMutator[State[E]] = {
    case (_, Causal(dm, _)) =>
      val dotsToRemove = elems.foldLeft(Set.empty[Dot]) {
        case (dots, e) => dm.get(e) match {
            case Some(ds) => dots union ds
            case None     => dots
          }
      }

      deltaState[E].make(
        cc = CContext[C].fromSet(dotsToRemove)
      )
  }

  def removeBy[E](cond: E => Boolean): DeltaMutator[State[E]] = {
    case (_, Causal(dm, _)) =>
      val removedDots = dm.collect {
        case (k, v) if cond(k) => v
      }.foldLeft(DotSet.empty)(_ union _)

      deltaState[E].make(
        cc = CContext[C].fromSet(removedDots)
      )
  }

  def clear[E](): DeltaMutator[State[E]] = {
    case (_, Causal(dm, _)) =>
      deltaState[E].make(
        cc = CContext[C].fromSet(DotMap[E, DotSet].dots(dm))
      )
  }
}

/** An AWSet (Add-Wins Set) is a Delta CRDT modeling a set.
  *
  * When an element is concurrently added and removed/cleared from the set then the add operation wins, i.e. the resulting set contains the element.
  */
abstract class AWSetInterface[E, Wrapper] extends CRDTInterface[AWSetInterface.State[E], Wrapper] {
  def elements: Set[E] = query(AWSetInterface.elements)

  def add(e: E): Wrapper = mutate(AWSetInterface.add(e))

  def addAll(elems: Iterable[E]): Wrapper = mutate(AWSetInterface.addAll(elems))

  def remove(e: E): Wrapper = mutate(AWSetInterface.remove(e))

  def removeAll(elems: Iterable[E]): Wrapper = mutate(AWSetInterface.removeAll(elems))

  def removeBy(cond: E => Boolean): Wrapper = mutate(AWSetInterface.removeBy(cond))

  def clear(): Wrapper = mutate(AWSetInterface.clear())
}