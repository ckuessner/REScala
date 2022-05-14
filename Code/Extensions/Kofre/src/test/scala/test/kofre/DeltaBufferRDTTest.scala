package test.kofre

import kofre.base.{Bottom, Defs, Lattice}
import kofre.decompose.containers.DeltaBufferRDT
import kofre.decompose.interfaces.EnableWinsFlag
import kofre.rga.{DeltaSequence, Vertex}
import org.scalatest.freespec.AnyFreeSpec

class DeltaBufferRDTTest extends munit.FunSuite {

  test("basic interaction") {

    val dbe = DeltaBufferRDT[EnableWinsFlag](Defs.genId())

    assertEquals(dbe.state.store, Bottom.empty[EnableWinsFlag])
    assert(!dbe.read)
    assertEquals(dbe.deltaBuffer, List.empty)

    val dis = dbe.enable().enable()
    assert(dis.read)
    val en = dis.disable()

    assert(!en.read)
    assertEquals(en.deltaBuffer.size, 3)

  }

}
