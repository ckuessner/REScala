package todo

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import loci.registry.Binding
import loci.serializer.jsoniterScala._
import org.scalajs.dom.{UIEvent, window}
import org.scalajs.dom.html.{Div, Input}
import rescala.default._
import rescala.extra.Tags._
import rescala.extra.lattices.delta.DietCC._
import kofre.decompose.Delta
import rescala.extra.lattices.delta.crdt.reactive.RGA
import rescala.extra.lattices.delta.crdt.reactive.RGA._
import rescala.extra.replication.LociDist
import scalatags.JsDom
import scalatags.JsDom.all._
import scalatags.JsDom.tags2.section
import scalatags.JsDom.{Attr, TypedTag}

class TodoAppUI(val storagePrefix: String) {

  implicit val stringCodec: JsonValueCodec[String] = JsonCodecMaker.make

  @scala.annotation.nowarn // Auto-application to `()`
  def getContents(): TypedTag[Div] = {

    val todoInputTag: JsDom.TypedTag[Input] = input(
      id          := "newtodo",
      `class`     := "new-todo",
      placeholder := "What needs to be done?",
      autofocus   := "autofocus"
    )

    val (createTodo, todoInputField) = inputFieldHandler(todoInputTag, onchange)

    val removeAll = Events.fromCallback[UIEvent](cb => button("remove all done todos", onclick := cb))

    val toggleAll = Events.fromCallback[UIEvent] { cb =>
      input(id := "toggle-all", name := "toggle-all", `class` := "toggle-all", `type` := "checkbox", onchange := cb)
    }

    val deltaEvt = Evt[Delta[RGA.State[TaskRef, DietMapCContext]]]

    val taskrefs = new TaskRefObj(toggleAll.event, storagePrefix)
    TaskRefs.taskrefObj = taskrefs
    val taskOps = new TaskOps(taskrefs)

    import Codecs._

    val rga = {
      Storing.storedAs(storagePrefix, taskOps.listInitial) { init =>
        Events.foldAll(init) { s =>
          Seq(
            createTodo act taskOps.handleCreateTodo(s),
            removeAll.event dyn { dt => _ => taskOps.handleRemoveAll(s, dt) },
            s.toList.map(_.removed) act taskOps.handleRemove(s),
            deltaEvt act taskOps.handleDelta(s)
          )
        }
      }(codecRGA)
    }

    LociDist.distributeDeltaCRDT(rga, deltaEvt, Todolist.registry)(
      Binding[RGA.State[TaskRef, DietMapCContext] => Unit]("tasklist")
    )

    val tasksList = rga.map { _.toList }

    val tasksData = tasksList.map(_.flatMap(_.task.value.read))

    val taskTags = tasksList.map(_.map(_.tag))

    val largeheader = window.location.hash.substring(1)

    div(
      `class` := "todoapp",
      header(
        `class` := "header",
        h1(if (largeheader.nonEmpty) largeheader else "todos"),
        todoInputField
      ),
      section(
        `class` := "main",
        `style` := Signal { if (tasksData().isEmpty) "display:hidden" else "" },
        toggleAll.value,
        label(`for` := "toggle-all", "Mark all as complete"),
        ul(
          `class` := "todo-list",
          taskTags.asModifierL
        )
      ),
      div(
        `class` := "footer",
        `style` := Signal { if (tasksData().isEmpty) "display:none" else "" },
        Signal {
          val remainingTasks = tasksData.value.count(!_.done)
          span(
            `class` := "todo-count",
            strong("" + remainingTasks),
            span(if (remainingTasks == 1)
              " item left"
            else " items left")
          )
        }.asModifier,
        Signal {
          removeAll.value(`class` := "clear-completed" + (if (!tasksData.value.exists(_.done)) " hidden" else ""))
        }.asModifier
      )
    )
  }

  def inputFieldHandler(tag: TypedTag[Input], attr: Attr): (Event[String], Input) = {
    val handler = Events.fromCallback[UIEvent](cb => tag(attr := cb))

    val todoInputField: Input = handler.value.render

    val handlerEvent =
      handler.event.map { e: UIEvent =>
        e.preventDefault()
        val res = todoInputField.value.trim
        todoInputField.value = ""
        res
      }

    (handlerEvent, todoInputField)
  }

}
