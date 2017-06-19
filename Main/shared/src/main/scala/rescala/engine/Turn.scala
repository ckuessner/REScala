package rescala.engine

import rescala.graph._



/**
  * The Turn interface glues the reactive interface and the propagation implementation together.
  *
  * @tparam S Struct type that defines the spore type used to manage the reactive evaluation
  */
trait Turn[S <: Struct] extends AlwaysTicket[S] {
  private[rescala] def makeDynamicReevaluationTicket(indeps: Set[Reactive[S]]): DynamicTicket[S]
  private[rescala] def makeStaticReevaluationTicket(): StaticTicket[S]
  private[rescala] def makeAdmissionPhaseTicket(): AdmissionTicket[S]
  private[rescala] def makeWrapUpPhaseTicket(): WrapUpTicket[S]

//  /**
//    * Synchronize for access (i.e., [[before]] or [[after]]) on this node when
//    * synchronization is unknown. Multiple invocations are redundant, but not harmful outside of an
//    * implementation-dependent performance penalty.
//    *
//    * @param reactive the reactive to be dynamically accessed
//    */
//  private[rescala] def dynamicDependencyInteraction(reactive: Reactive[S]): Unit
//
//  /**
//    * Read value from before this turn. Only call this if you know that you are synchronized with this node:
//    * Reads on dependencies where an edge exists (e.g., reading a static dependency) is always synchronized.
//    * Reads on other nodes must be synchronized through dynamicDependencyInteraction first.
//    * @param pulsing the node to be read
//    * @tparam P the node's storage type
//    * @return the stored value from before this turn
//    */
//  private[rescala] def before[P](pulsing: Pulsing[P, S]): P
//  /**
//    * Read value from after this turn. Implementations may return the node's current value, including
//    * changes already made by this turn but disregarding potential future changes, or may suspend to
//    * return only the value that is final until the end of this turn.
//    * Only call this if you know that you are synchronized with this node:
//    * Reads on dependencies where an edge exists (e.g., reading a static dependency) is always synchronized.
//    * Reads on other nodes must be synchronized through dynamicDependencyInteraction first.
//    * @param pulsing the node to be read
//    * @tparam P the node's storage type
//    * @return the stored value from after this turn
//    */
//  private[rescala] def after[P](pulsing: Pulsing[P, S]): P


  private[rescala] def create[P, R <: Reactive[S]](incoming: Set[Reactive[S]], valuePersistency: ValuePersistency[P])(instantiateReactive: S#State[P, S] => R): R

  /**
    * Registers a new handler function that is called after all changes were written and committed.
    *
    * @param f Handler function to register.
    */
  private[rescala] def observe(f: () => Unit): Unit
}

