package central

import central.Bindings._
import kofre.base.DecomposeLattice
import loci.communicator.tcp.TCP
import loci.registry.Registry

import scala.io.StdIn.readLine

class Checkpointer(listenPort: Int) {
  val registry = new Registry

  var checkpoint: Int = 0

  var checkpointToDelta: Map[Int, SetState] = Map()

  val minCheckpointSize: Int = 10

  val bottom: SetState = DecomposeLattice[SetState].empty

  var fullState: SetState = bottom

  def run(): Unit = {
    registry.bind(assessCheckpointBinding) {
      case SyncMessage(cp, deltaState) =>
        val apply = (cp + 1 to checkpoint).map(checkpointToDelta).toList

        DecomposeLattice[SetState].diff(fullState, deltaState) match {
          case None =>
            println(s"No new changes since checkpoint")
            CheckpointMessage(checkpoint, apply, bottom)

          case Some(newChanges) =>
            val newAtoms = DecomposeLattice[SetState].decompose(newChanges)

            if (newAtoms.size < minCheckpointSize) {
              println(s"Only ${newAtoms.size} new atoms, no new checkpoint created")
              CheckpointMessage(checkpoint, apply, newChanges)
            } else {
              checkpoint += 1
              checkpointToDelta = checkpointToDelta.updated(checkpoint, newChanges)
              fullState = DecomposeLattice[SetState].merge(fullState, newChanges)
              println(s"Created checkpoint $checkpoint")

              CheckpointMessage(checkpoint, apply, bottom)
            }
        }
    }

    registry.bind(isCheckpointerBinding) { () => true }

    println(registry.listen(TCP(listenPort)))

    readLine()
    System.exit(0)
  }
}
