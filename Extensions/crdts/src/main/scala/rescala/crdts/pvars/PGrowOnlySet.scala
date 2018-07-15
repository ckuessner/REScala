package rescala.crdts.pvars

import rescala.crdts.pvars.Publishable.PVarFactory
import rescala.crdts.statecrdts.StateCRDT
import rescala.crdts.statecrdts.sets.GSet
import rescala.default._

case class PGrowOnlySet[A](initial: GSet[A] = GSet[A](),
                           internalChanges: Evt[GSet[A]] = Evt[GSet[A]](),
                           externalChanges: Evt[GSet[A]] = Evt[GSet[A]]()) extends Publishable[Set[A], GSet[A]] {

  def add(a: A): Unit = internalChanges.fire(crdtSignal.readValueOnce.add(a))

  def contains(a: A): Boolean = crdtSignal.readValueOnce.contains(a)
}

object PGrowOnlySet {
  /**
    * Allows creation of DistributedSets by passing a set of initial values.
    */
  def apply[A](values: Set[A]): PGrowOnlySet[A] = {
    val init: GSet[A] = implicitly[StateCRDT[Set[A], GSet[A]]].fromValue(values)
    new PGrowOnlySet[A](init)
  }

  //noinspection ConvertExpressionToSAM
  implicit def PGrowOnlySetFactory[A]: PVarFactory[PGrowOnlySet[A]] =
    new PVarFactory[PGrowOnlySet[A]] {
      override def apply(): PGrowOnlySet[A] = PGrowOnlySet[A]()
    }
}
