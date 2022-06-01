package lore

import scala.language.implicitConversions
import AST._
import cats.parse.{Parser => P, Parser0 => P0, Rfc5234}
import cats.parse.Rfc5234.{alpha, char, digit, sp}
import cats.implicits._
import cats._
import cats.data.NonEmptyList

object Parser:
  // helpers
  val ws: P0[Unit] = sp.rep0.void // whitespace
  val id: P[ID] = (alpha ~ (alpha | digit).rep0).string
  val number: P[TNum] = digit.rep.string.map(i => TNum(Integer.parseInt(i)))
  val argT: P[TArgT] = // args with type
    (((id <* sp.? ~ P.char(':')) <* sp.rep0) ~ typeName).map((id, typ) =>
      TArgT(id, typ)
    )
  val typeName: P[Type] = P.recursive { rec =>
    (id ~ (P.char('[') ~ ws *> rec.repSep(P.char(',') ~ ws) <* ws ~ P.char(
      ']'
    )).?)
      .map {
        case (outer, Some(inner)) => Type(outer, inner.toList)
        case (outer, None)        => Type(outer, List.empty)
      }
  }

  // helper definition for parsing sequences of expressions
  val parseSeq = (factor: P[Term], separator: P[String]) =>
    (factor ~
      (((ws.with1.soft *> separator <* ws))
        ~ factor).rep0)

  // basic terms
  val _var: P[TVar] = id.map(TVar(_)) // variables

  // arithmetic expressions
  val arithmExpr: P[Term] = P.defer(addSub)
  val addSub: P[Term] =
    P.defer(parseSeq(divMul, P.stringIn(List("+", "-")))).map(evalArithm)
  val divMul: P[Term] =
    P.defer(parseSeq(arithFactor, P.stringIn(List("/", "*")))).map(evalArithm)
  val parens: P[Term] =
    (P.char('(') ~ ws).with1 *> arithmExpr <* ws ~ P.char(')')
  val arithFactor: P[Term] =
    P.defer(
      parens | fieldAcc | functionCall | number.backtrack | _var
    )
  def evalArithm(seq: (Term, List[(String, Term)])): Term = seq match
    case (x, Nil)            => x
    case (l, ("*", r) :: xs) => TMul(left = l, right = evalArithm(r, xs))
    case (l, ("/", r) :: xs) => TDiv(left = l, right = evalArithm(r, xs))
    case (l, ("+", r) :: xs) => TAdd(left = l, right = evalArithm(r, xs))
    case (l, ("-", r) :: xs) => TSub(left = l, right = evalArithm(r, xs))
    case sth =>
      throw new IllegalArgumentException(s"Not an arithmetic expression: $sth")

  // boolean expressions
  val booleanExpr: P[Term] = P.defer(quantifier | implication)

  // primitives
  val tru: P[TBoolean] = P.string("true").as(TTrue)
  val fls: P[TBoolean] = P.string("false").as(TFalse)
  val boolParens: P[Term] = // parantheses
    (P.char('(') ~ ws).with1 *> P
      .defer(implication) <* ws ~ P.char(')')
  val boolFactor: P[Term] =
    boolParens
      | P.defer(inSet)
      | P.defer(numComp)
      | P.defer(fieldAcc)
      | P.defer(functionCall)
      | tru.backtrack
      | fls.backtrack
      | _var

  // helper for boolean expressions with two sides
  val boolTpl = (factor: P[Term], separator: P[Unit]) =>
    factor ~ ((ws.soft ~ separator.backtrack ~ ws) *> factor).?
  val implication: P[Term] =
    P.defer(boolTpl(equality, P.string("==>"))).map {
      case (left, None)        => left
      case (left, Some(right)) => TImpl(left = left, right = right)
    }
  val equality: P[Term] =
    P.defer(boolTpl(inequality, P.string("==") <* P.char('>').unary_!))
      .map {
        case (left, None)        => left
        case (left, Some(right)) => TEq(left = left, right = right)
      }
  val inequality: P[Term] =
    P.defer(boolTpl(conjunction, P.string("!="))).map {
      case (left, None)        => left
      case (left, Some(right)) => TIneq(left = left, right = right)
    }

  // helper for boolean expressions with arbitrarily long sequences like && and ||
  val boolSeq = (factor: P[Term], separator: String) =>
    parseSeq(factor, P.string(separator).as(separator)).map(evalBoolSeq)
  val conjunction: P[Term] =
    P.defer(boolSeq(disjunction, "&&"))
  val disjunction: P[Term] =
    P.defer(boolSeq(boolFactor, "||"))
  def evalBoolSeq(seq: (Term, Seq[(String, Term)])): Term =
    seq match
      case (root, Nil) => root
      case (root, ("||", x) :: xs) =>
        TDisj(left = root, right = evalBoolSeq(x, xs))
      case (root, ("&&", x) :: xs) =>
        TConj(left = root, right = evalBoolSeq(x, xs))
      case sth =>
        throw new IllegalArgumentException(s"Not a boolean expression: $sth")

  // set expressions
  val inSetFactor: P[Term] =
    P.defer(fieldAcc | functionCall | number.backtrack | _var)
  val inSet: P[TBoolean] = P
    .defer(
      ((inSetFactor <* ws).soft <* P
        .string("in") ~ ws) ~ inSetFactor
    )
    .map { (left, right) =>
      TInSet(left, right)
    }

  // number comparisons
  val numComp: P[TBoolean] = (
    arithmExpr.soft ~ (ws.soft.with1 *> P
      .stringIn(List("<=", ">=", "<", ">")) <* ws) ~ arithmExpr
  )
    .map { case ((l, op), r) =>
      op match
        case "<=" => TLeq(left = l, right = r)
        case ">=" => TGeq(left = l, right = r)
        case "<"  => TLt(left = l, right = r)
        case ">"  => TGt(left = l, right = r)
    }

  // quantifiers
  val quantifierVars: P[NonEmptyList[TArgT]] =
    (argT).repSep(P.char(',').surroundedBy(ws))
  val triggers: P0[List[TViper]] = P.unit.as(List[TViper]())
  val forall: P[TForall] =
    (((P.string("forall") ~ ws *> quantifierVars) <* P.string(
      "::"
    ) ~ ws) ~ triggers ~ booleanExpr).map { case ((vars, triggers), body) =>
      TForall(vars = vars, triggers = triggers, body = body)
    }
  val exists: P[TExists] =
    ((P.string("exists") ~ ws *> quantifierVars <* P
      .string("::")
      .surroundedBy(ws)) ~ booleanExpr).map { case (vars, body) =>
      TExists(vars = vars, body = body)
    }
  val quantifier: P[TQuantifier] = forall | exists

  // reactives
  val reactive: P[TReactive] = P.defer(source | derived)
  val source: P[TSource] =
    (P.string("Source(") ~ ws *> P.defer(term) <* P.char(')')).map((body) =>
      TSource(body)
    )
  val derived: P[TDerived] =
    (P.string("Derived{") ~ ws *> P.defer(term) <* P.char('}')).map((body) =>
      TDerived(body)
    )

  // bindings
  val bindingLeftSide: P[TArgT] =
    (P.string("val") ~ ws *> argT <* P.char('=').surroundedBy(ws))
  val binding: P[TAbs] =
    P.defer((bindingLeftSide <* P.char('=').surroundedBy(ws)) ~ term).map {
      case (TArgT(name, _type), term) =>
        TAbs(name = name, _type = _type, body = term)
    }

  // object orientation
  val args = P.defer0(term.repSep0(P.char(',') ~ ws))
  val objFactor = P.defer(functionCall | _var)
  val fieldAcc: P[TFAcc] =
    P.defer(
      objFactor.soft ~
        (P.char('.') *> id ~ (P.char('(') *> args <* (ws ~ P.char(')'))).?).rep
    ).map((parent, rest) => evalFieldAcc(parent, rest.toList))
  def evalFieldAcc(s: (Term, List[(ID, Option[List[Term]])])): TFAcc =
    s match
      case (parent: TFAcc, Nil) => parent
      case (parent, (field, args) :: Nil) =>
        TFAcc(parent = parent, field = field, args = args.getOrElse(Seq()))
      case (parent, (field, args) :: rest) =>
        evalFieldAcc(
          TFAcc(parent = parent, field = field, args = args.getOrElse(Seq())),
          rest
        )
      case _ =>
        throw new IllegalArgumentException(s"Not a valid field access: $s")

  // functions
  val functionCall: P[TFunC] = (id.soft ~ (P.char('(') *> args) <* P.char(')'))
    .map { (id, arg) =>
      TFunC(name = id, args = arg)
    }

  // type aliases
  val typeAlias: P[TTypeAl] =
    (P.string("type") ~ ws *> id ~ (P.char('=').surroundedBy(ws) *> typeName))
      .map((n, t) => TTypeAl(name = n, _type = t))

  // programs are sequences of terms
  val term: P[Term] =
    fieldAcc | functionCall | typeAlias | booleanExpr | number.backtrack | _var
  val prog: P[NonEmptyList[Term]] = term.repSep(ws).between(ws, ws ~ P.end)
