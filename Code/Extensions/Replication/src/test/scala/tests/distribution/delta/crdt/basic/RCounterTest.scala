package tests.distribution.delta.crdt.basic

import kofre.decompose.interfaces.RCounterInterface.RCounter
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import rescala.extra.lattices.delta.JsoniterCodecs._

import rescala.extra.replication.AntiEntropy
import tests.distribution.delta.crdt.basic.NetworkGenerators._
import kofre.decompose.interfaces.RCounterInterface.RCounterSyntax
import kofre.decompose.containers.{AntiEntropyCRDT, Network}

import scala.collection.mutable
import scala.util.Random

object RCounterGenerators {
  def genRCounter: Gen[AntiEntropyCRDT[RCounter]] = for {
    nInc   <- Gen.posNum[Int]
    nDec   <- Gen.posNum[Int]
    nReset <- Gen.posNum[Int]
    nFresh <- Gen.posNum[Int]
  } yield {
    val network = new Network(0, 0, 0)
    val ae      = new AntiEntropy[RCounter]("a", network, mutable.Buffer())

    val ops = Random.shuffle(List.fill(nInc)(0) ++ List.fill(nDec)(1) ++ List.fill(nReset)(2) ++ List.fill(nFresh)(3))

    ops.foldLeft(AntiEntropyCRDT[RCounter](ae)) {
      case (c, 0) => c.increment()
      case (c, 1) => c.decrement()
      case (c, 2) => c.reset()
      case (c, 3) => c.fresh()
      // default case is only needed to stop the compiler from complaining about non-exhaustive match
      case (c, _) => c
    }
  }

  implicit def arbRCounter: Arbitrary[AntiEntropyCRDT[RCounter]] = Arbitrary(genRCounter)
}

class RCounterTest extends AnyFreeSpec with ScalaCheckDrivenPropertyChecks {
  import RCounterGenerators._

  "increment" in forAll { counter: AntiEntropyCRDT[RCounter] =>
    val incremented = counter.increment()

    assert(
      incremented.value == counter.value + 1,
      s"Incrementing the counter should increase its value by 1, but ${incremented.value} does not equal ${counter.value} + 1"
    )
  }

  "decrement" in forAll { counter: AntiEntropyCRDT[RCounter] =>
    val decremented = counter.decrement()

    assert(
      decremented.value == counter.value - 1,
      s"Decrementing the counter should decrease its value by 1, but ${decremented.value} does not equal ${counter.value} - 1"
    )
  }

  "fresh" in forAll { counter: AntiEntropyCRDT[RCounter] =>
    val fresh = counter.fresh()

    assert(
      fresh.value == counter.value,
      s"Calling fresh should not change the value of the counter, but ${fresh.value} does not equal ${counter.value}"
    )
  }

  "reset" in forAll { counter: AntiEntropyCRDT[RCounter] =>
    val reset = counter.reset()

    assert(
      reset.value == 0,
      s"After resetting the counter its value should be 0, but ${reset.value} does not equal 0"
    )
  }

  "concurrent increment/decrement/fresh" in forAll { (opA: Either[Unit, Boolean], opB: Either[Unit, Boolean]) =>
    val network = new Network(0, 0, 0)

    val aea = new AntiEntropy[RCounter]("a", network, mutable.Buffer("b"))
    val aeb = new AntiEntropy[RCounter]("b", network, mutable.Buffer("a"))

    val ca0 = opA match {
      case Left(_)      => AntiEntropyCRDT[RCounter](aea).increment()
      case Right(false) => AntiEntropyCRDT[RCounter](aea).decrement()
      case Right(true)  => AntiEntropyCRDT[RCounter](aea).fresh()
    }
    val cb0 = opB match {
      case Left(_)      => AntiEntropyCRDT[RCounter](aeb).increment()
      case Right(false) => AntiEntropyCRDT[RCounter](aeb).decrement()
      case Right(true)  => AntiEntropyCRDT[RCounter](aeb).fresh()
    }

    AntiEntropy.sync(aea, aeb)

    val ca1 = ca0.processReceivedDeltas()
    val cb1 = cb0.processReceivedDeltas()

    val sequential = opB match {
      case Left(_)      => ca0.increment()
      case Right(false) => ca0.decrement()
      case Right(true)  => ca0.fresh()
    }

    assert(
      ca1.value == sequential.value,
      s"Concurrent execution of increment/decrement/fresh should have the same effect as sequential execution, but ${ca1.value} does not equal ${sequential.value}"
    )
    assert(
      cb1.value == sequential.value,
      s"Concurrent execution of increment/decrement/fresh should have the same effect as sequential execution, but ${cb1.value} does not equal ${sequential.value}"
    )
  }

  "concurrent reset and increment/decrement without fresh" in forAll { op: Boolean =>
    val network = new Network(0, 0, 0)

    val aea = new AntiEntropy[RCounter]("a", network, mutable.Buffer("b"))
    val aeb = new AntiEntropy[RCounter]("b", network, mutable.Buffer("a"))

    val ca0 = AntiEntropyCRDT[RCounter](aea).increment()
    AntiEntropy.sync(aea, aeb)
    val cb0 = AntiEntropyCRDT[RCounter](aeb).processReceivedDeltas()

    val ca1 = if (op) ca0.increment() else ca0.decrement()
    val cb1 = cb0.reset()

    AntiEntropy.sync(aea, aeb)

    val ca2 = ca1.processReceivedDeltas()
    val cb2 = cb1.processReceivedDeltas()

    assert(
      ca2.value == 0,
      s"Concurrent reset should win over increment/decrement without fresh, but ${ca2.value} does not equal 0"
    )
    assert(
      cb2.value == 0,
      s"Concurrent reset should win over increment/decrement without fresh, but ${cb2.value} does not equal 0"
    )
  }

  "concurrent reset and increment/decrement with fresh" in forAll { op: Boolean =>
    val network = new Network(0, 0, 0)

    val aea = new AntiEntropy[RCounter]("a", network, mutable.Buffer("b"))
    val aeb = new AntiEntropy[RCounter]("b", network, mutable.Buffer("a"))

    val ca0 = AntiEntropyCRDT[RCounter](aea).increment()
    AntiEntropy.sync(aea, aeb)
    val cb0 = AntiEntropyCRDT[RCounter](aeb).processReceivedDeltas()

    val ca1 = if (op) ca0.fresh().increment() else ca0.fresh().decrement()
    val cb1 = cb0.reset()

    AntiEntropy.sync(aea, aeb)

    val ca2 = ca1.processReceivedDeltas()
    val cb2 = cb1.processReceivedDeltas()

    val sequential = if (op) cb1.increment() else cb1.decrement()

    assert(
      ca2.value == sequential.value,
      s"Concurrent increment/decrement with fresh and reset should have the same result as executing increment/decrement sequentially after reset, but ${ca2.value} does not equal ${sequential.value}"
    )
    assert(
      cb2.value == sequential.value,
      s"Concurrent increment/decrement with fresh and reset should have the same result as executing increment/decrement sequentially after reset, but ${cb2.value} does not equal ${sequential.value}"
    )
  }

  "convergence" in forAll {
    (
        nOpsA1: (Byte, Byte, Byte, Byte),
        nOpsB1: (Byte, Byte, Byte, Byte),
        nOpsA2: (Byte, Byte, Byte, Byte),
        nOpsB2: (Byte, Byte, Byte, Byte),
        network: Network
    ) =>
      {
        val aea = new AntiEntropy[RCounter]("a", network, mutable.Buffer("b"))
        val aeb = new AntiEntropy[RCounter]("b", network, mutable.Buffer("a"))

        val opsA1 = Random.shuffle(List.fill(nOpsA1._1.toInt)(0) ++ List.fill(nOpsA1._2.toInt)(1) ++ List.fill(
          nOpsA1._3.toInt
        )(2) ++ List.fill(nOpsA1._4.toInt)(3))
        val opsB1 = Random.shuffle(List.fill(nOpsB1._1.toInt)(0) ++ List.fill(nOpsB1._2.toInt)(1) ++ List.fill(
          nOpsB1._3.toInt
        )(2) ++ List.fill(nOpsB1._4.toInt)(3))
        val opsA2 = Random.shuffle(List.fill(nOpsA2._1.toInt)(0) ++ List.fill(nOpsA2._2.toInt)(1) ++ List.fill(
          nOpsA2._3.toInt
        )(2) ++ List.fill(nOpsA2._4.toInt)(3))
        val opsB2 = Random.shuffle(List.fill(nOpsB2._1.toInt)(0) ++ List.fill(nOpsB2._2.toInt)(1) ++ List.fill(
          nOpsB2._3.toInt
        )(2) ++ List.fill(nOpsB2._4.toInt)(3))

        def applyOps(counter: AntiEntropyCRDT[RCounter], ops: List[Int]): AntiEntropyCRDT[RCounter] = {
          ops.foldLeft(counter) {
            case (c, 0) => c.increment()
            case (c, 1) => c.decrement()
            case (c, 2) => c.reset()
            case (c, 3) => c.fresh()
            // default case is only needed to stop the compiler from complaining about non-exhaustive match
            case (c, _) => c
          }
        }

        val ca0 = applyOps(AntiEntropyCRDT[RCounter](aea), opsA1)
        val cb0 = applyOps(AntiEntropyCRDT[RCounter](aeb), opsB1)

        AntiEntropy.sync(aea, aeb)

        val ca1 = applyOps(ca0.processReceivedDeltas(), opsA2)
        val cb1 = applyOps(cb0.processReceivedDeltas(), opsB2)

        AntiEntropy.sync(aea, aeb)
        network.startReliablePhase()
        AntiEntropy.sync(aea, aeb)

        val ca2 = ca1.processReceivedDeltas()
        val cb2 = cb1.processReceivedDeltas()

        assert(
          ca2.value == cb2.value,
          s"After synchronization messages were reliably exchanged all replicas should converge, but ${ca2.value} does not equal ${cb2.value}"
        )
      }
  }
}
