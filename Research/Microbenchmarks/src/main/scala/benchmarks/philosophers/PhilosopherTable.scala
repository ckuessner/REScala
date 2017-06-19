package benchmarks.philosophers

import java.util.concurrent.atomic.AtomicInteger

import benchmarks.philosophers.PhilosopherTable._
import org.openjdk.jmh.infra.Blackhole
import rescala.core.{Engine, Struct}
import rescala.reactives._

class PhilosopherTable[S <: Struct](philosopherCount: Int, work: Long)(implicit val engine: Engine[S]) {


  val seatings = createTable(philosopherCount)

  val eaten = new AtomicInteger(0)

  seatings.foreach { seating =>
    seating.vision.observe { state =>
      if (state == Done) {
        Blackhole.consumeCPU(work)
      }
    }
  }

  def createTable(tableSize: Int): Seq[Seating[S]] = {
    def mod(n: Int): Int = (n + tableSize) % tableSize

    val phils = for (i <- 0 until tableSize) yield Var[Philosopher, S](Thinking)

    val forks = for (i <- 0 until tableSize) yield {
      val nextCircularIndex = mod(i + 1)
      Signals.lift(phils(i), phils(nextCircularIndex))(calcFork(i.toString, nextCircularIndex.toString))
    }

    for (i <- 0 until tableSize) yield {
      val vision = Signals.lift(forks(i), forks(mod(i - 1)))(calcVision(i.toString))
      Seating(i, phils(i), forks(i), forks(mod(i - 1)), vision)
    }
  }

  def tryEat(seating: Seating[S]): Boolean =
    engine.transactionWithWrapup(seating.philosopher) { t =>
      val forksAreFree = t.now(seating.vision) == Ready
      if (forksAreFree) seating.philosopher.admit(Eating)(t)
      forksAreFree
    } /* propagation executes here */ { (forksWereFree, t) =>
      if (forksWereFree) assert(t.now(seating.vision) == Done, s"philosopher should be done after turn")
      forksWereFree
    }
}

object PhilosopherTable {

  def calcFork(leftName: String, rightName: String)(leftState: Philosopher, rightState: Philosopher): Fork =
    (leftState, rightState) match {
      case (Thinking, Thinking) => Free
      case (Eating, _) => Taken(leftName)
      case (_, Eating) => Taken(rightName)
    }

  def calcVision(ownName: String)(leftFork: Fork, rightFork: Fork): Vision =
    (leftFork, rightFork) match {
      case (Free, Free) => Ready
      case (Taken(`ownName`), Taken(`ownName`)) => Done
      case (Taken(name), _) => BlockedBy(name)
      case (_, Taken(name)) => BlockedBy(name)
    }


  // ============================================= Infrastructure ========================================================

  sealed trait Philosopher
  case object Thinking extends Philosopher
  case object Eating extends Philosopher

  sealed trait Fork
  case object Free extends Fork
  case class Taken(name: String) extends Fork

  sealed trait Vision
  case object Ready extends Vision
  case object Done extends Vision
  case class BlockedBy(name: String) extends Vision


  // ============================================ Entity Creation =========================================================

  case class Seating[S <: Struct](placeNumber: Int, philosopher: Var[Philosopher, S], leftFork: Signal[Fork, S], rightFork: Signal[Fork, S], vision: Signal[Vision, S])

}
