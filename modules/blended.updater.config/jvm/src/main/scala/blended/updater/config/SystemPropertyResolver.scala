package blended.updater.config

import java.util.regex.Matcher

object SystemPropertyResolver {

  def resolve(props : Map[String, String]) : Map[String, String] = {

    def toReplace(props : Map[String, String]) : Map[String, String] = props.filter { prop =>
      val p1 = prop._2.indexOf("${")
      val p2 = prop._2.lastIndexOf("}")
      p1 >= 0 && p2 > p1
    }

    def replaceCandidate(props : Map[String, String]) : Option[String] = {

      toReplace(props) match {
        case m if m.isEmpty => None
        case m =>
          val start = m.head._2.indexOf("${")
          val end = m.head._2.indexOf("}", start)
          Some(m.head._2.substring(start + 2, end))
      }
    }

    def singleReplace(props : Map[String, String], key : String) : Map[String, String] = props.collect {
      case p =>
        val replaceKey = "${" + key + "}"
        val replaceValue = props.get(key) match {
          case None    => throw new Exception(s"Unable to resove System property [$key]")
          case Some(s) => s
        }

        p._2.contains(replaceKey) match {
          case true  => (p._1, p._2.replaceAll("\\Q" + replaceKey + "\\E", Matcher.quoteReplacement(replaceValue)))
          case false => p
        }
    }

    replaceCandidate(props) match {
      case None => props
      case Some(s) =>
        val replaced = singleReplace(props, s)
        // TODO: add cycle check
        //        if (toReplace(props).size == toReplace(replaced).size)
        //          throw new Exception(s"Unable to resolve System properties [key=$s]")
        resolve(replaced)
    }
  }
}
