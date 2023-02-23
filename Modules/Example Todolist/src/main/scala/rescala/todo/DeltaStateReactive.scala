package rescala.todo

import rescala.core.{CreationTicket, Derived, DynamicTicket, ReInfo, ReadAs}
import rescala.default.*

case class DeltaWithState[Delta, DState](delta: Seq[Delta], state: DState)

class DeltaStateReactive[Delta, Combined](
    initState: State[DeltaWithState[Delta, Combined]],
    deltaInput: ReadAs.of[State, Delta],
    applyDelta: (Combined, Delta) => Combined,
    handlers: Seq[(DynamicTicket[State], Combined) => Delta],
    override val info: ReInfo,
) extends Derived with ReadAs[DeltaWithState[Delta, Combined]] {
  override type Value    = DeltaWithState[Delta, Combined]
  override type State[V] = rescala.default.State[V]
  override protected[rescala] def state: State[Value]        = initState
  override protected[rescala] def commit(base: Value): Value = base.copy(delta = Nil)

  override protected[rescala] def reevaluate(input: ReIn): Rout = {
    input.trackDependencies(Set(deltaInput))
    val sourceVal     = input.dependStatic(deltaInput)
    var deltas        = List(sourceVal)
    val applyExternal = applyDelta(input.before.state, sourceVal)
    val combined = handlers.foldLeft(applyExternal) { case (current, handler) =>
      val delta = handler(input, current)
      deltas ::= delta
      applyDelta(current, delta)
    }

    input.withValue(DeltaWithState(deltas, combined))
  }
  override def read(v: DeltaWithState[Delta, Combined]): DeltaWithState[Delta, Combined] = v
}

object DeltaStateReactive {
  def create[DState, Delta](
      init: DState,
      deltaInput: ReadAs.of[State, Delta],
      applyDelta: (DState, Delta) => DState,
      handlers: Seq[(DynamicTicket[State], DState) => Delta]
  )(implicit name: ReInfo, creationTicket: CreationTicket[State]): DeltaStateReactive[Delta, DState] =
    creationTicket.create(Set(deltaInput), DeltaWithState(List.empty[Delta], init), needsReevaluation = false)(state =>
      new DeltaStateReactive(state, deltaInput, applyDelta, handlers, name)
    )
}
