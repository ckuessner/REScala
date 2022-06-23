package compiler

import scala.annotation.compileTimeOnly

object ExtensionMethods {
  extension[P <: Product] (p: P)
    @compileTimeOnly("This method can only be used in expressions that are translated to C code")
    def deepCopy(): P = ???
    
  extension[T] (arr: Array[T])
    @compileTimeOnly("This method can only be used in expressions that are translated to C code")
    def deepCopy(): Array[T] = ???
    
  extension[T] (opt: Option[T])
    @compileTimeOnly("This method can only be used in expressions that are translated to C code")
    def deepCopy(): Option[T] = ???

  extension[L, R] (either: Either[L, R])
    @compileTimeOnly("This method can only be used in expressions that are translated to C code")
    def deepCopy(): Either[L, R] = ???
}
