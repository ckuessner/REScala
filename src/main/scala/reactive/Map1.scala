package reactive

import clangast.WithContext
import clangast.decl.CFunctionDecl
import clangast.traversal.CASTMapper
import clangast.types.CType
import compiler.MacroCompiler

import scala.quoted.*

case class Map1[A, R](input: Event[A], cType: WithContext[CType], f: WithContext[CFunctionDecl]) extends Event[R] {
  override def inputs: List[ReSource] = List(input)

  override val baseName: String = "map"
}

object Map1 {
  class Map1Factory[A, R](input: Event[A]) {
    inline def apply[C <: MacroCompiler](inline funName: String)(inline f: A => R)(using mc: C): Map1[A, R] =
      Map1(
        input,
        mc.compileType[R],
        mc.compileAnonFun(f, funName)
      )
  }
}

extension [A] (input: Event[A])
  inline def map[R]: Map1.Map1Factory[A, R] = new Map1.Map1Factory(input)
    
  inline def observe[C <: MacroCompiler](inline funName: String = "observe")(inline f: A => Unit)(using mc: C): Map1[A, Unit] =
    map(funName)(f)
