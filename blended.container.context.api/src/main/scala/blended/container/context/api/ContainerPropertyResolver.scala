package blended.container.context.api

import blended.util.logging.Logger
import org.springframework.expression.Expression
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext

import scala.collection.mutable
import scala.util.Try

object ContainerPropertyResolver {

  type Resolver = String => String
  type Modifier = (String, String) => String

  private[this] val log = Logger[ContainerPropertyResolver.type]

  private[this] val resolveStartDelim = "$[["
  private[this] val resolveEndDelim = "]]"

  private[this] val evalStartDelim = "${{"
  private[this] val evalEndDelim = "}}"

  private[this] val parser = new SpelExpressionParser()

  private[this] val elCache : mutable.Map[String, Expression] = mutable.Map.empty

  private[this] def parseExpression(exp: String) : Try[Expression] = Try {

    elCache.get(exp) match {
      case Some(e) => e
      case None =>
        val r = parser.parseExpression(exp)
        elCache += (exp -> r)
        r
    }
  }


  private[this] def extractVariableElement(line: String, startDelim: String, endDelim: String) : (String, String, String) = {

    line.lastIndexOf(startDelim) match {
      case -1 => ("", line, "")
      case start => line.indexOf(endDelim, start) match {
        case -1 => throw new PropertyResolverException(s"Error decoding replacement pattern [$line] : missing end delimiter [$endDelim]")
        case end => (line.substring(0, start), line.substring(start + startDelim.length, end), line.substring(end + endDelim.length))
      }
    }
  }

  // these are the valid modifiers in a resolver expression
  private[this] val modifiers : Map[String, Modifier] = Map(
    "upper" -> { case (s : String, _ : String) => s.toUpperCase() },

    "lower" -> { case (s : String, _ : String) => s.toLowerCase() },

    "capitalize" -> { case (s : String, _ : String) => s.capitalize },

    "right" -> { case (s: String, param: String) =>
      val n = param.toInt
      if (n >= s.length) s else s.takeRight(n)
    },

    "left" -> { case (s: String, param : String) =>
      val n = param.toInt
      if (n >= s.length) s else s.take(n)
    },

    "replace" -> { case (s: String, param: String) =>
      val replace = param.split(":")
      if (replace.length != 2) {
        s
      } else {
        s.replaceAll(replace(0), replace(1))
      }
    }
  )

  private[this] def resolver(idSvc: ContainerIdentifierService) : Map[String, Resolver] = Map(
    ContainerIdentifierService.containerId -> ( _ => idSvc.uuid )
  )

  private[this] def extractModifier(s : String) : Option[(Modifier, String)] = {
    val pos = s.indexOf(":")
    val (modName, params) = if (pos != -1) (s.substring(0, pos), s.substring(pos + 1)) else (s, "")
    modifiers.get(modName).map { m => (m, params) }
  }

  private[this] def processRule(idSvc: ContainerIdentifierService, rule: String, additionalProps: Map[String, Any]) : String = {

    log.trace(s"Processing rule [$rule]")

    // A rule can have any number of parameterized modifiers, spearated by ","
    val (ruleName : String, modifier : List[String]) = rule.indexOf("(") match {
      case -1 => (rule, List.empty)
      case s => (
        rule.substring(0, s),
        rule.substring(s + 1, rule.indexOf(")", s)).split(",").toList
      )
    }

    log.trace(s"rule [$ruleName], [${modifier.mkString(",")}]")

    // Now we need to find the proper modifiers to apply with their parameters
    val mods : List[(Modifier, String)] = modifier.map(extractModifier).collect {
      case Some(m) => m
    }

    val props = Option(idSvc.properties).getOrElse(Map.empty)

    // First, we resolve the rule from the environment vars or System properties
    // The resolution is mandatory
    var result : String = props.get(ruleName) match {
      case Some(s) => s
      case None =>
        Option(
          additionalProps.getOrElse(
            rule,
            System.getenv().getOrDefault(
              ruleName, System.getProperty(ruleName)
            )
          )
        ) match {
          case Some(s) => s.toString()
          case None =>
            resolver(idSvc).get(ruleName) match {
              case Some(r) => r(ruleName)
              case None => throw new PropertyResolverException(s"Unable to resolve property [$rule]")
            }
        }
    }

    // Then we apply the collected modifiers left to right to the resolved value
    result = mods.foldLeft[String](result)((a,b) => b._1(a, b._2))

    result
  }

  private[api] def resolve(idSvc: ContainerIdentifierService, line: String, additionalProps: Map[String, Any] = Map.empty) : AnyRef = {
    log.trace(s"Resolving [$line]")
    // First we check if we have replacements in "Blended Style"
    line.indexOf(resolveStartDelim) match {
      case n if n < 0 =>
        // We don't have any
        line.indexOf(evalStartDelim) match {
          case i if i < 0 => line
          case i if i >= 0 =>
            val (prefix, eval, suffix) = extractVariableElement(line, evalStartDelim, evalEndDelim)

            if (prefix.isEmpty && suffix.isEmpty) {
              evaluate(idSvc, eval, additionalProps)
            } else {
              resolve(idSvc, prefix + evaluate(idSvc, eval, additionalProps) + suffix, additionalProps)
            }
        }
      case n if n >= 0 =>
        val (prefix, rule, suffix) = extractVariableElement(line, resolveStartDelim, resolveEndDelim)
        resolve(idSvc, prefix + processRule(idSvc, rule, additionalProps) + suffix, additionalProps)
    }
  }

  private[api] def evaluate(
    idSvc: ContainerIdentifierService, line: String, additionalProps : Map[String, Any] = Map.empty
  ) : AnyRef = {

    val context = new StandardEvaluationContext()
    context.setRootObject(idSvc)

    additionalProps.foreach { case (k,v) =>
      log.trace(s"Injecting variable into SpEL context : $k $v ${v.getClass.getName}")
      context.setVariable(k,v)
    }
    context.setVariable("idSvc", idSvc)

    val exp = parseExpression(line).get
    val result = exp.getValue(context)

    result
  }
}
