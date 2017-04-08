package blended.container.context

import org.slf4j.LoggerFactory

object ContainerPropertyResolver {

  private[this] val log = LoggerFactory.getLogger(classOf[ContainerPropertyResolver])

  private[this] val startDelim = "$[["
  private[this] val endDelim = "]]"

  def extractRule(line: String) : (String, String, String) = {
    line.lastIndexOf(startDelim) match {
      case -1 => ("", line, "")
      case start => line.indexOf(endDelim, start) match {
        case -1 => throw new PropertyResolverException(s"Error decoding replacement pattern [${line}] : missing end delimiter [$endDelim]")
        case end => (line.substring(0, start), line.substring(start + startDelim.length, end), line.substring(end + endDelim.length))
      }
    }
  }

  def processRule(idSvc: ContainerIdentifierService, rule: String) : String = {

    type Resolver = String => String
    type Modifier = (String, String) => String

    val modifiers : Map[String, Modifier] = Map(
      "upper" -> { case (s : String, param : String) => s.toUpperCase() },

      "lower" -> { case (s : String, param : String) => s.toLowerCase() },

      "capitalize" -> { case (s : String, param: String) => s.capitalize },

      "right" -> { case (s: String, param: String) =>
        val n = param.toInt
        if (n >= s.length) s else s.takeRight(n)
      },

      "left" -> { case (s: String, param: String) =>
        val n = param.toInt
        if (n >= s.length) s else s.take(n)
      }
    )

    val resolvers : Map[String, Resolver] = Map(
      ContainerIdentifierService.containerId -> ( _ => idSvc.getUUID() )
    )

    val (ruleName : String, modifier : List[String]) = rule.indexOf("(") match {
      case -1 => (rule, List.empty)
      case (s) => (
        rule.substring(0, s),
        rule.substring(s+1, rule.indexOf(")", s)).split(",").toList
      )
    }

    val mods : List[(Modifier, String)] = modifier.map { m =>
      val pos = m.indexOf(":")
      if (pos != -1) (m.substring(0, pos), m.substring(pos + 1)) else (m, "")
    }.filter { case (modName, param) => modifiers.contains(modName) }.map{ case (modName, param) =>
      (modifiers(modName), param)
    }

    var result = Option(idSvc.getProperties().getProperty(ruleName)) match {
      case Some(s) => s
      case None =>
        log.debug(s"Resolving [$ruleName] from System properties.")
        Option(System.getProperties.getProperty(ruleName)) match {
          case Some(s) => s
          case None =>
            log.debug(s"Resolving [$ruleName] from special Resolvers")
            resolvers.get(ruleName) match {
              case Some(r) => (r(ruleName))
              case None => throw new PropertyResolverException(s"Unable to resolve property [$rule]")
            }
        }
    }

    result = mods.foldLeft[String](result)((a,b) => b._1(a, b._2))

    result
  }

  def resolve(idSvc: ContainerIdentifierService, line: String) : String = {

    var result = line

    while(result.indexOf(startDelim) != -1) {
      val (prefix, rule, suffix) = extractRule(result)
      result = prefix + processRule(idSvc, rule) + suffix
    }

    log.debug(s"Resolved [$line] to [$result]")
    result
  }

}

class ContainerPropertyResolver