package lore

import AST._
import cats.implicits._

object ViperBackend:

  def toViper(ast: Seq[Term]): String =
    //// step 1: collect registry of all reactives
    val graph: Map[String, TReactive] = allReactives(ast)

    // step 2: analyze invariants //
    val invariants: Seq[TInvariant] = ast.collect({ case i: TInvariant => i })

    // collect relevant reactives per invariant
    val reactivesPerInvariant: Map[TInvariant, Set[ID]] = invariants
      .map(i =>
        // get reactives that this invariant mentions
        val directDeps = uses(i).filter(name => graph.keySet.contains(name))
        // collect (transitive) inputs of these reactives
        val allDeps = directDeps.flatMap(name => getSubgraph(name, graph))
        (i, allDeps)
      )
      .toMap

    // compile invariants to macros
    // replace references to derived reactives with macro calls
    def transformer(term: Term) = term match
      case _var @ TVar(name) =>
        // check if is reference to derived reactive
        graph.get(name) match
          case Some(d: DerivedReactive) =>
            val args = uses(d)
              .filter(r => graph.keys.toSet.contains(r))
              .toSeq
              .sortBy(_.name)
            TFunC(d.name, args)
          case _ => _var
      case _ => term

    val invariantsCompiled: Seq[String] = invariants.map(i =>
      val transformed = traverseFromNode(i, transformer)
      invariantToMacro(
        transformed,
        reactivesPerInvariant(i)
          .collect({ case s: TSource => s })
          .map(_.name.name)
      )
    )

    // step 3: compile reactives
    val sourcesCompiled: Seq[String] = graph.values
      .collect({ case s: TSource => s })
      .map(
        sourceToField(_)
      )
      .toSeq

    val derivedCompiled: Seq[String] = graph.values
      .collect({ case d: TDerived => d })
      .map(d => derivedToMacro(graph, d))
      .toSeq

//     // step 4: compile transactions
//     val transactions: Seq[Transaction] = ast.collect({ case t: Transaction =>
//       t
//     })
//     val transactionsCompiled = transactions.map(transactionToMethods(ast, _))

    s"""|// sources
          |${sourcesCompiled.mkString("\n")}
          |// derived
          |${derivedCompiled.mkString("\n")}
          |// invariants
          |${invariantsCompiled.mkString("\n")}
          |
          |// transactions
          |${transactionsCompiled.mkString("\n")}
          |""".stripMargin

  // def invariantToMacro(invariant: Invariant, inputs: Set[String]): String =
  //     val inputsString = inputs.toSeq.sorted.mkString(", ")
  //     s"define inv_${invariant.index}($inputsString) ${expressionToViper(invariant.body)}"

  // def sourceToField(source: SourceReactive): String =
  //     val typeAnn = source.typeAnn match
  //         case Some(t) => t
  //         case None => throw new Exception(s"Source reactive ${source.name.name} needs a type annotation to be compiled to Viper.")
  //     s"field ${source.name.name}: $typeAnn"

  // def derivedToMacro(graph: Map[String, Reactive], d: DerivedReactive): String =
  //     val usedReactives = uses(d).filter(r => graph.keys.toSet.contains(r))
  //     val body = expressionToViper(d.body)
  //     val argsString = usedReactives.toSeq.sorted.mkString(", ")

  //     s"define ${d.name.name}($argsString) $body"

  // def transactionToMethods(ast: Seq[ParsedExpression], transaction: AST.Transaction): String =
  //     val argsString = transaction.args.map{
  //         case (name, Some(typeName)) => s"${name.name}: ${typeName.name}"
  //         case (name, None) => throw new Exception(s"Transaction $transaction needs type annotations to be compiled to Viper.")
  //     }

  //     val graph = allReactives(ast)

  //     // divide transaction into subtransactions
  //     val subtransactions: Seq[String] = transaction.reactives.map(names =>
  //         val reactives: Seq[Reactive] = names.map(n => graph.get(n.name) match
  //             case Some(r) => r
  //             case None => throw Exception(s"Reactive ${n.name} not found in graph."))
  //         val subGraph: Set[Reactive] = reactives.map(r => getDependants(r, ast))
  //             .toSet.flatten
  //         val subGraphNames: Set[String] = subGraph.map(r => r.name.name)

  //         val sourceReactives: Seq[SourceReactive] =
  //             reactives.collect({case s: SourceReactive => s})
  //         val derivedReactives: Seq[DerivedReactive] =
  //             subGraph.collect({case d: DerivedReactive => d}).toSeq

  //         val invariants: Set[Invariant] = ast.collect({
  //             case i: Invariant if !uses(i).intersect(subGraphNames).isEmpty => i}).toSet
  //         val invariantsStrings: Set[String] = invariants.map(i => expressionToViper(i.body))
  //         val invariantsAfter: Set[String] =
  //             invariantsStrings.map(s =>
  //                 derivedReactives.foldl(s){case (acc, derivedReactive) =>
  //                     acc.replaceAll(derivedReactive.name.name, s"${derivedReactive.name.name}_")}
  //             )

  //         val graphString = sourceReactives.map{
  //             case (SourceReactive(_, id, body, Some(typeAnn))) => s"${id.name}: $typeAnn"
  //             case (r @ SourceReactive(_, name, body, None)) => throw new Exception(s"Reactive $r needs type annotation to be compiled to Viper.")
  //         }
  //         val graphStringAfter = sourceReactives.map{
  //             case (SourceReactive(_, id, body, Some(typeAnn))) => s"${id.name}_: $typeAnn"
  //             case (r @ SourceReactive(_, name, body, None)) => throw new Exception(s"Reactive $r needs type annotation to be compiled to Viper.")
  //         }

  //         val preconditions = transaction.preconditions.map(p =>
  //             "requires " + expressionToViper(p.body))
  //         val postconditions = transaction.postconditions.map(p =>
  //             "ensures " + expressionToViper(p.body))

  //         s"""|method ${transaction.name.name}_${reactives.map(_.name.name).mkString("_")}(
  //             |  // graph state:
  //             |  ${graphString.mkString(",\n  ")},
  //             |  // transaction arguments:
  //             |  ${argsString.mkString(",\n  ")})
  //             |returns (
  //             |  ${graphStringAfter.mkString(",\n  ")}
  //             |)
  //             |// preconditions
  //             |${preconditions.mkString("\n")}
  //             |// relevant invariants
  //             |${invariantsStrings.map(i => "requires " + i).mkString("\n")}
  //             |// postconditions
  //             |${postconditions.mkString("\n")}
  //             |// relevant invariants
  //             |${invariantsAfter.map(i => "ensures " + i).mkString("\n")}
  //             |{
  //             |${expressionToViper(transaction.body)}
  //             |}
  //             |""".stripMargin
  //     )

  //     subtransactions.mkString("\n")

  def expressionToViper(expression: Term): String = expression match
    case TTrue       => "true"
    case TFalse      => "false"
    case TEq(l, r)   => s"${expressionToViper(l)} == ${expressionToViper(r)}"
    case TIneq(l, r) => s"${expressionToViper(l)} != ${expressionToViper(r)}"
    case TDisj(l, r) => s"${expressionToViper(l)} || ${expressionToViper(r)}"
    case TConj(l, r) => s"${expressionToViper(l)} && ${expressionToViper(r)}"
    case TImpl(l, r) => s"${expressionToViper(l)} ==> ${expressionToViper(r)}"
    case TLt(l, r)   => s"${expressionToViper(l)} < ${expressionToViper(r)}"
    case TGt(l, r)   => s"${expressionToViper(l)} > ${expressionToViper(r)}"
    case TLeq(l, r)  => s"${expressionToViper(l)} <= ${expressionToViper(r)}"
    case TGeq(l, r)  => s"${expressionToViper(l)} >= ${expressionToViper(r)}"
    case TForall(vars, triggers, body) =>
      val varString =
        vars.map { case TArgT(name, t) => s"$name: $t" }.toList.mkString(", ")
      triggers match
        case Nil => s"forall $varString :: ${expressionToViper(body)}"
        case t =>
          s"forall $varString :: {${t.map(expressionToViper).mkString(", ")}} ${expressionToViper(body)}"
    case TAdd(l, r)     => s"${expressionToViper(l)} + ${expressionToViper(r)}"
    case TSub(l, r)     => s"${expressionToViper(l)} - ${expressionToViper(r)}"
    case TDiv(l, r)     => s"${expressionToViper(l)} / ${expressionToViper(r)}"
    case TMul(l, r)     => s"${expressionToViper(l)} * ${expressionToViper(r)}"
    case TNum(i)        => s"$i"
    case TParens(inner) => s"(${expressionToViper(inner)})"
    case TFunC(name, args) =>
      val argsString = args.map(a => expressionToViper(a)).mkString(", ")
      s"$name($argsString)"
    case TFCall(parent, field, args) => // field call
      val argsString = args.map(a => expressionToViper(a)).mkString(", ")
      s"${expressionToViper(parent)}.$field($argsString)"
    case TInSet(l, r) => s"${expressionToViper(l)} in ${expressionToViper(r)}"
    case exp =>
      throw new IllegalArgumentException(
        s"Expression $exp not allowed in Viper expressions!"
      )
