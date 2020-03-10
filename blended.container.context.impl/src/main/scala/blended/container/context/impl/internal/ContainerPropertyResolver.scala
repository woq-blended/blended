package blended.container.context.impl.internal

import java.util.regex.{Matcher, Pattern}

import blended.container.context.api.{ContainerContext, PropertyResolverException, SpelFunctions}
import blended.security.crypto.ContainerCryptoSupport
import blended.util.RichTry._
import blended.util.logging.Logger
import org.springframework.expression.Expression
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

class ContainerPropertyResolver(
  ctUuid : String,
  properties : Map[String, String],
  cryptoSupport : ContainerCryptoSupport
) {

  private case class ExtractedElement(
    prefix : String,
    pattern : String,
    postfix : String,
    modifier : String = ""
  )

  private[this] var ctCtxt : Option[ContainerContext] = None

  type Resolver = String => Try[String]
  type Modifier = (String, String) => Try[String]

  private[this] val log = Logger[ContainerPropertyResolver]

  private[this] val resolveStartChar = "["
  private[this] val resolveEndChar = "]"
  private[this] val evalStartChar = "{"
  private[this] val evalEndChar = "}"

  private[this] val startDelim : String => Pattern = { s =>
    val buf : StringBuffer = new StringBuffer()
    buf.append("(\\$\\" + s + ")") // Match a modifier if any
    buf.append("([^\\" + s + "]*)(\\" + s + ")") // capture the modifier name as match group
    Pattern.compile(buf.toString)
  }

  private[this] val resolveStartDelim : Pattern = startDelim(resolveStartChar)
  private[this] val resolveEndDelim : String = s"$resolveEndChar$resolveEndChar"

  private[this] val evalStartDelim : Pattern = startDelim(evalStartChar)
  private[this] val evalEndDelim : String = s"$evalEndChar$evalEndChar"

  private[this] val parser = new SpelExpressionParser()

  private[this] val elCache : mutable.Map[String, Expression] = mutable.Map.empty

  private[internal] def setCtCtxt(ctxt : ContainerContext) : Unit = ctCtxt = Some(ctxt)

  private[this] def parseExpression(exp : String) : Try[Expression] = Try {

    elCache.get(exp) match {
      case Some(e) => e
      case None =>
        val r = parser.parseExpression(exp)
        elCache += (exp -> r)
        r
    }
  }

  private[this] def lastIndexOfPattern(current : Int, next : Int, s : String, p : Pattern) : Int = {

    val m : Matcher = p.matcher(s)

    if (m.find(Math.max(next, 0))) {
      lastIndexOfPattern(m.start(), m.start() + m.group(0).length, s, p)
    } else {
      current
    }
  }

  private[this] def extractVariableElement(
    line : String,
    startDelim : Pattern,
    endDelim : String
  ) : ExtractedElement = {

    val idx : Int = lastIndexOfPattern(-1, -1, line, startDelim)

    idx match {
      case -1 =>
        ExtractedElement("", line, "")
      case start =>
        line.indexOf(endDelim, start) match {
          case -1 => throw new PropertyResolverException(s"Error decoding replacement pattern [$line] : missing end delimiter [$endDelim]")
          case end =>

            val subline = line.substring(start, end + endDelim.length)
            val subMatcher : Matcher = startDelim.matcher(subline)
            subMatcher.find()

            val patternStart = start + subMatcher.group(0).length

            ExtractedElement(
              prefix = line.substring(0, start),
              line.substring(patternStart, end),
              postfix = line.substring(end + endDelim.length),
              modifier = subMatcher.group(2)
            )
        }
    }
  }

  // these are the valid modifiers in a resolver expression
  private[this] val modifiers : Map[String, Modifier] = Map(
    "upper" -> { case (s : String, _ : String) => Try { s.toUpperCase() } },

    "lower" -> { case (s : String, _ : String) => Try { s.toLowerCase() } },

    "capitalize" -> { case (s : String, _ : String) => Try { s.capitalize } },

    "right" -> {
      case (s : String, param : String) => Try {
        val n = param.toInt
        if (n >= s.length) s else s.takeRight(n)
      }
    },

    "left" -> {
      case (s : String, param : String) => Try {
        val n = param.toInt
        if (n >= s.length) s else s.take(n)
      }
    },

    "replace" -> {
      case (s : String, param : String) => Try {
        val replace = param.split(":")
        if (replace.length != 2) {
          s
        } else {
          s.replaceAll(replace(0), replace(1))
        }
      }
    }
  )

  private[this] val resolver : Map[String, Resolver] = Map(
    ContainerContext.containerId -> { _ => Success(ctUuid) }
  )

  private[this] def extractModifier(s : String) : Option[(Modifier, String)] = {
    val pos = s.indexOf(":")
    val (modName, params) = if (pos != -1) (s.substring(0, pos), s.substring(pos + 1)) else (s, "")
    modifiers.get(modName).map { m => (m, params) }
  }

  private[this] def processRule(rule : String, additionalProps : Map[String, Any]) : Try[String] = {

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

    // First, we resolve the rule from the environment vars or System properties
    // The resolution is mandatory
    val applyRules : Try[String] = properties.get(ruleName) match {
      case Some(s) => Success(s)
      case None =>
        Option(
          additionalProps.getOrElse(
            rule,
            System.getenv().getOrDefault(
              ruleName, System.getProperty(ruleName)
            )
          )
        ) match {
            case Some(s) => Success(s.toString())
            case None =>
              resolver.get(ruleName) match {
                case Some(r) => r(ruleName)
                case None    => Failure(new PropertyResolverException(s"Unable to resolve property [$rule]"))
              }
          }
    }

    // Then we apply the collected modifiers left to right to the resolved value
    val result : Try[String] = mods.foldLeft[Try[String]](applyRules)( (current, value) => current match {
      case f @ Failure(_) => f
      case Success(s) => value._1(s, value._2)
    })

    result
  }

  def resolve(line : String, additionalProps : Map[String, Any] = Map.empty) : Try[AnyRef] = Try {
    // First we check if we have replacements in "Blended Style"

    /**
     * TODO: Map the modifier to installable services
     * delayed : do not evaluate the inner expression any further
     * encrypted : resolve an encrypted value
     */
    if (lastIndexOfPattern(-1, -1, line, resolveStartDelim) != -1) {
      val e = extractVariableElement(line, resolveStartDelim, resolveEndDelim)
      e.modifier match {
        case "delayed" =>
          resolve(e.prefix, additionalProps).unwrap + e.pattern + resolve(e.postfix, additionalProps).unwrap

        case "encrypted" =>
          val decrypted : String = cryptoSupport.decrypt(e.pattern).get
          resolve(e.prefix, additionalProps).unwrap.toString() +
            resolve(decrypted, additionalProps).unwrap.toString() +
            resolve(e.postfix, additionalProps).unwrap.toString()

        case _ =>
          // First we resolve the inner expression to resolve any nested expressions
          val inner = resolve(e.pattern, additionalProps).unwrap.toString
          // then we resolve the entire line with the inner expression resolved
          resolve(e.prefix + processRule(inner, additionalProps).unwrap + e.postfix, additionalProps).unwrap

      }
    } else {
      lastIndexOfPattern(-1, -1, line, evalStartDelim) match {
        case i if i < 0 =>
          line
        case i if i >= 0 =>
          val e = extractVariableElement(line, evalStartDelim, evalEndDelim)
          if (e.prefix.isEmpty && e.postfix.isEmpty) {
            evaluate(e.pattern, additionalProps)
          } else {
            resolve(e.prefix + evaluate(e.pattern, additionalProps).toString() + e.postfix, additionalProps).unwrap
          }
      }
    }
  }

  // TODO : Should this be Option[AnyRef] ??
  private[impl] def evaluate(
    line : String, additionalProps : Map[String, Any] = Map.empty
  ) : AnyRef = {

    val context = new StandardEvaluationContext()
    classOf[SpelFunctions].getDeclaredMethods().foreach { m =>
      context.registerFunction(m.getName(), m)
    }

    ctCtxt.foreach{ c =>
      context.setRootObject(c)
      context.setVariable("ctCtxt", c)
    }

    additionalProps.foreach {
      case (k, v) =>
        context.setVariable(k, v)
    }

    val exp = parseExpression(line).get

    Option(exp.getValue(context)) match {
      case None =>
        log.warn(s"Could not resolve expression [${line}], using empty String")
        ""
      case Some(r) =>
        log.trace(s"Evaluated [$line] to [$r][${r.getClass().getName()}]")
        r
    }
  }
}
