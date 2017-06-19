package rescala.testhelper

import rescala.core.{Engine, Struct}
import rescala.fullmv.FullMVEngine
import rescala.stm.STMEngine

object TestEngines {
  val all: List[Engine[_ <: Struct]] = rescala.Engines.all ::: List(STMEngine.stm, FullMVEngine)
}
