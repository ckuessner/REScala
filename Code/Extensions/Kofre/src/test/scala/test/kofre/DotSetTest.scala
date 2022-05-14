package test.kofre

import kofre.base.{DecomposeLattice, Lattice}
import kofre.time.{Dots, Dot}
import kofre.contextual.{AsCausalContext, WithContext}
import kofre.dotted.DotSet
import org.scalacheck.Prop
import org.scalacheck.Prop.*
import test.kofre.DataGenerator.*

class DotSetTest extends munit.ScalaCheckSuite {

  test("empty") {
    assert(
      DotSet.empty.isEmpty,
      s"DotSet.empty should be empty, but ${DotSet.empty} is not empty"
    )

  }

  test("Manual Tests") {
    val d1 = Dot("1", 1)
    val d2 = Dot("2", 1)

    val s1 = Set(d1)
    assert(s1.contains(d1))

    val s2 = Set(d2)
    val c1 = Set(d1)
    val c2 = Set(d1, d2) // d1 is already deleted in the second causal context

    val mergedStore = Lattice.merge(
      WithContext(s1, Dots.fromSet(c1)),
      WithContext(s2, Dots.fromSet(c2))
      ).store

    assert(!mergedStore.contains(d1))
    assert(mergedStore.contains(d2))
  }
  property("merge 1") {
    forAll { (s1: Set[Dot], t1: Set[Dot], s2: Set[Dot], t2: Set[Dot]) =>
      // add all current values to their causal contexts
      val c1 = s1 ++ t1
      val c2 = s2 ++ t2

      // commutativity
      val m1 = Lattice.merge(WithContext(s1, Dots.fromSet(c1)), WithContext(s2, Dots.fromSet(c2)))
      val m2 = Lattice.merge(WithContext(s2, Dots.fromSet(c2)), WithContext(s1, Dots.fromSet(c1)))
      assert(m1 == m2)

      // check if all elements were added to the new causal context
      for (e <- c1) yield {
        assert(m1.context.contains(e))
      }
      for (e <- c2) yield {
        assert(m1.context.contains(e))
      }

      val deadElements = c1.filter(!s1.contains(_)) ++ c2.filter(!s2.contains(_))
      val newElements  = (s1 union s2) -- deadElements

      // check that already deleted elements are not added again
      for (e <- deadElements) yield {
        assert(!m1.store.contains(e))
      }

      // check that the new store contains all new elements
      for (e <- newElements) yield {
        assert(m1.store.contains(e))
      }

      Prop.passed
    }
  }

  property("merge 2") {
    forAll { (dsA: Dots, deletedA: Dots, dsB: Dots, deletedB: Dots) =>
      val ccA = dsA union deletedA
      val ccB = dsB union deletedB

      val WithContext(dsMerged, ccMerged) = DecomposeLattice[WithContext[DotSet]].merge(
        WithContext(DotSet(dsA), ccA),
        WithContext(DotSet(dsB), ccB)
      )

      assert(
        ccMerged == (ccA union ccB),
        s"DotSet.merge should have the same effect as set union on the causal context, but $ccMerged does not equal ${ccA union ccB}"
      )
      assert(
        dsMerged.toSet subsetOf (dsA union dsB).toSet,
        s"DotSet.merge should not add new elements to the DotSet, but $dsMerged is not a subset of ${dsA union dsB}"
      )
      assert(
        (dsMerged intersect (deletedA diff dsA)).isEmpty,
        s"The DotSet resulting from DotSet.merge should not contain dots that were deleted on the lhs, but $dsMerged contains elements from ${deletedA diff dsA}"
      )
      assert(
        (dsMerged intersect (deletedB diff dsB)).isEmpty,
        s"The DotSet resulting from DotSet.merge should not contain dots that were deleted on the rhs, but $dsMerged contains elements from ${deletedB diff dsB}"
      )
    }

  }
  property("leq") {
    forAll { (dsA: Dots, deletedA: Dots, dsB: Dots, deletedB: Dots) =>
      val ccA = dsA union deletedA
      val ccB = dsB union deletedB

      assert(
        WithContext(dsA, ccA) <= WithContext(dsA, ccA),
        s"DotSet.leq should be reflexive, but returns false when applied to ($dsA, $ccA, $dsA, $ccA)"
      )

      val WithContext(dsMerged, ccMerged) = DecomposeLattice[WithContext[Dots]].merge(
        WithContext(dsA, ccA),
        WithContext(dsB, ccB)
      )

      assert(
        WithContext(dsA, ccA) <= WithContext(dsMerged, ccMerged),
        s"The result of DotSet.merge should be larger than its lhs, but DotSet.leq returns false when applied to ($dsA, $ccA, $dsMerged, $ccMerged)"
      )
      assert(
        WithContext(dsB, ccB) <= WithContext(dsMerged, ccMerged),
        s"The result of DotSet.merge should be larger than its rhs, but DotSet.leq returns false when applied to ($dsB, $ccB, $dsMerged, $ccMerged)"
      )
    }
  }

  property("decompose all") {
    forAll { (ds: Dots, deleted: Dots) =>
      val cc = ds union deleted

      val decomposed = WithContext(ds, cc).decomposed
      val WithContext(dsMerged, ccMerged) = decomposed.foldLeft(WithContext(Dots.empty, Dots.empty)) {
        case (WithContext(dsA, ccA), WithContext(dsB, ccB)) =>
          DecomposeLattice[WithContext[Dots]].merge(WithContext(dsA, ccA), WithContext(dsB, ccB))
      }

      assertEquals(
        dsMerged,
        ds,
        s"Merging the list of atoms returned by DotSet.decompose should produce an equal DotSet, but $dsMerged does not equal $ds (while decomposed was $decomposed)"
      )
      assertEquals(
        ccMerged,
        cc,
        s"Merging the list of atoms returned by DotSet.decompose should produce an equal Causal Context, but $ccMerged does not equal $cc"
      )
    }
  }
}