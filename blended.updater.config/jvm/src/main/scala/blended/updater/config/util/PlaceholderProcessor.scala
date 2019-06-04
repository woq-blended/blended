package blended.updater.config.util

import java.io._
import java.util.regex.{Matcher, Pattern}

import scala.io.Source
import scala.util.Try

class PlaceholderProcessor(props : Map[String, String], openSeq : String, closeSeq : String, escapeChar : Char = '\\', failOnMissing : Boolean) {

  private[this] val pattern2 = Pattern.compile(s"^\\Q${openSeq}\\E(.*?)\\Q${closeSeq}\\E")

  def process(in : InputStream, out : OutputStream) : Try[Unit] = Try {
    val is = new LineNumberReader(new InputStreamReader(in))
    val ps = new PrintStream(new BufferedOutputStream(out))
    var line : String = null
    try {
      Iterator.continually(is.readLine()).takeWhile(_ != null).map { line =>
        process(line).get
      } foreach { line =>
        ps.println(line)
      }
    } finally {
      ps.flush()
      ps.close()
    }
  }

  def process(in : File, out : File) : Try[File] = Try {
    val ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(out)))
    try {
      Source.fromFile(in).getLines().map { line =>
        process(line).get
      }.foreach { line =>
        ps.println(line)
      }
    } finally {
      ps.flush()
      ps.close()
    }
    out
  }

  def process(in : String) : Try[String] = Try {
    val sb = new StringBuffer()
    var escaped = false
    var toProcess : String = in

    while (!toProcess.isEmpty()) {
      val head = toProcess.head
      if (escaped) {
        sb.append(head)
        escaped = false
        toProcess = toProcess.tail
      } else if (head == escapeChar) {
        escaped = true
        toProcess = toProcess.tail
      } else {
        val m = pattern2.matcher(toProcess)
        if (m.find()) {
          val variable = m.group(1)
          val replacement = props.get(variable).getOrElse {
            if (failOnMissing) sys.error(s"No property found to replace: ${openSeq}${variable}${closeSeq}")
            else m.group(0)
          }
          m.appendReplacement(sb, Matcher.quoteReplacement(replacement))
          val newTail = new StringBuffer()
          m.appendTail(newTail)
          toProcess = newTail.toString()
        } else {
          sb.append(head)
          toProcess = toProcess.tail
        }
      }
    }
    sb.toString()
  }
}
