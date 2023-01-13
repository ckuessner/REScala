package kofre.deprecated.containers

import kofre.base.{Bottom, DecomposeLattice, Id}
import kofre.time.Dots
import kofre.dotted.{Dotted, DottedDecompose, DottedLattice}
import kofre.syntax.{Named, PermCausal, PermCausalMutate, PermIdMutate, PermQuery}

type DeltaBufferDotted[State] = DeltaBuffer[Dotted[State]]

object DeltaBufferDotted {

  /** Creates a new PNCounter instance
    *
    * @param replicaID Unique id of the replica that this instance is located on
    */
  def apply[State](replicaID: Id)(using bot: Bottom[Dotted[State]]): DeltaBuffer[Dotted[State]] =
    new DeltaBuffer(replicaID, bot.empty, List())

  def empty[State](replicaID: Id, init: State): DeltaBuffer[Dotted[State]] =
    new DeltaBuffer(replicaID, Dotted(init), List())

  def apply[State](replicaID: Id, init: State): DeltaBuffer[Dotted[State]] = empty(replicaID, init)
}

/** ReactiveCRDTs are Delta CRDTs that store applied deltas in their deltaBuffer attribute. Middleware should regularly
  * take these deltas and ship them to other replicas, using applyDelta to apply them on the remote state. After deltas
  * have been read and propagated by the middleware, it should call resetDeltaBuffer to empty the deltaBuffer.
  */
case class DeltaBuffer[State](
    replicaID: Id,
    state: State,
    deltaBuffer: List[Named[State]] = Nil
) {

  def applyDelta(source: Id, delta: State)(using DecomposeLattice[State]): DeltaBuffer[State] =
    DecomposeLattice[State].diff(state, delta) match {
      case Some(stateDiff) =>
        val stateMerged = DecomposeLattice[State].merge(state, stateDiff)
        new DeltaBuffer(replicaID, stateMerged, Named(source, stateDiff) :: deltaBuffer)
      case None => this
    }

  def resetDeltaBuffer(): DeltaBuffer[State] = copy(deltaBuffer = List())
}

object DeltaBuffer {

  given contextPermissions[L: DottedDecompose]: PermCausalMutate[DeltaBuffer[Dotted[L]], L] =
    new PermCausalMutate[DeltaBuffer[Dotted[L]], L] {
      override def query(c: DeltaBuffer[Dotted[L]]): L = c.state.store
      override def mutateContext(
          container: DeltaBuffer[Dotted[L]],
          withContext: Dotted[L]
      ): DeltaBuffer[Dotted[L]] = container.applyDelta(container.replicaID, withContext)
      override def context(c: DeltaBuffer[Dotted[L]]): Dots = c.state.context
    }

  given plainPermissions[L: DecomposeLattice : Bottom]: PermIdMutate[DeltaBuffer[L], L] =
    new PermIdMutate[DeltaBuffer[L], L] {
      override def replicaId(c: DeltaBuffer[L]): Id = c.replicaID
      override def mutate(c: DeltaBuffer[L], delta: L): DeltaBuffer[L] =
        c.applyDelta(c.replicaID, delta)
      override def query(c: DeltaBuffer[L]): L = c.state
    }
}
