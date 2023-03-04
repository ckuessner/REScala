package lore
import lore.AST._
import munit.FunSuite
import OverlapAnalysis._
import cats.implicits._
import cats.data.NonEmptyList

class BackendUtilsTests extends FunSuite {
  // simple reaches test
  test("reaches") {
    val prog = """|val a: Source[Int] = Source(0)
                  |val b: Source[Int] = Source(2)
                  |val c: Derived[Int] = Derived{a + b}
                  |val d: Derived[Int] = Derived{a}
                  |
                  |val i: Unit = Interaction[Int][Int]
                  |  .executes{0}
                  |  .modifies(a)
                  |val j: Unit = Interaction[Int][Int]
                  |  .executes{0}
                  |  .modifies(b)""".stripMargin
    val ast: NonEmptyList[Term] = Parser.parse(prog) match
      case Left(e)      => throw Exception(e.show)
      case Right(value) => value
    val ctx: CompilationContext =
      flattenInteractions(CompilationContext(ast.toList))

    assertEquals(reaches(ctx.interactions("i"), ctx), Set("a", "c", "d"))
    assertEquals(reaches(ctx.interactions("j"), ctx), Set("b", "c"))
  }
}
