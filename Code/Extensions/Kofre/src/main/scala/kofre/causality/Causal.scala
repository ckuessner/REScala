package kofre.causality

import kofre.Lattice
import kofre.causality.CausalContext
import kofre.causality.DotStore.{DotFun, DotMap, DotSet}
import kofre.causality.Causal

case class Causal[A](store: A, context: CausalContext) {
  def dotStore: A = store
  def causalContext: CausalContext = context
  def cc: CausalContext = context
}



// See: Delta state replicated data types (https://doi.org/10.1016/j.jpdc.2017.08.003)
object Causal {
  def bottom[D: DotStore]: Causal[D] = Causal(DotStore[D].bottom, CausalContext.empty)

  // (s, c) ⨆ (s', c') = ((s ∩ s') ∪ (s \ c') ∪ (s' \ c), c ∪ c')
  implicit def CausalWithDotSetLattice: Lattice[Causal[DotSet]] = (left, right) => {
    val inBoth = left.dotStore & right.dotStore
    val newInLeft =
      left.dotStore.filterNot(dot => dot.time <= right.causalContext.clockOf(dot.replicaId).get.time)
    val newInRight =
      right.dotStore.filterNot(dot => dot.time <= left.causalContext.clockOf(dot.replicaId).get.time)

    val mergedCausalContext = left.causalContext.union(right.causalContext)
    Causal(inBoth ++ newInLeft ++ newInRight, mergedCausalContext)
  }

  // (m, c) ⨆ (m', c') = ( {k -> m(k) ⨆ m'(k) | k ∈ dom m ∩ dom m'} ∪
  //                       {(d, v) ∈ m  | d ∉ c'} ∪
  //                       {(d, v) ∈ m' | d ∉ c},
  //                      c ∪ c')
  implicit def CausalWithDotFunLattice[V: Lattice]: Lattice[Causal[DotFun[V]]] = (left, right) => {
    Causal(
      (left.dotStore.keySet & right.dotStore.keySet map { (dot: Dot) =>
        (dot, Lattice.merge(left.dotStore(dot), right.dotStore(dot)))
      }).toMap
      ++ left.dotStore.filterNot { case (dot, _) => right.causalContext.contains(dot) }
      ++ right.dotStore.filterNot { case (dot, _) => left.causalContext.contains(dot) },
      left.causalContext.union(right.causalContext)
      )
  }

  // (m, c) ⨆ (m', c') = ( {k -> v(k) | k ∈ dom m ∩ dom m' ∧ v(k) ≠ ⊥}, c ∪ c')
  //                      where v(k) = fst((m(k), c) ⨆ (m'(k), c'))
  implicit def CausalWithDotMapLattice[K, V: DotStore](implicit
                                                       vSemiLattice: Lattice[Causal[V]]
                                                      ): Lattice[Causal[DotMap[K, V]]] =
    (left: Causal[DotMap[K, V]], right: Causal[DotMap[K, V]]) =>
      Causal(
        ((left.dotStore.keySet union right.dotStore.keySet) map { key =>
          val leftCausal  = Causal(left.dotStore.getOrElse(key, DotStore[V].bottom), left.causalContext)
          val rightCausal = Causal(right.dotStore.getOrElse(key, DotStore[V].bottom), right.causalContext)
          key -> Lattice[Causal[V]].merge(leftCausal, rightCausal).dotStore
        } filterNot {
           case (key, dotStore) => DotStore[V].bottom == dotStore
         }).toMap,
        left.causalContext.union(right.causalContext)
        )
}