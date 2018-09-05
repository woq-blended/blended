import java.nio.file.Files

import sbt._
import sbt.Keys._
import sbt.io.syntax
import scala.util.matching.Regex
import Regex.quoteReplacement

object FilterResources extends AutoPlugin {

  object autoImport {
    val filterSources = settingKey[Seq[File]]("Resource to filter")
    val filterTargetDir = settingKey[File]("Target directory for the filtered files")
    val filterResources = taskKey[Seq[(File, String)]]("filter the unfiltered resources")
    val filterProperties = settingKey[Map[String, String]]("Extra properties to be applied while filtering")
    val filterRegex = settingKey[String]("The replacement pattern")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = inConfig(Compile)(Seq(
    filterProperties := Map.empty,
    filterSources := Seq(baseDirectory.value / "src" / "main" / "filterResources"),
    filterTargetDir := target.value,
    filterRegex := """\$\{(.+?)\}""",
    filterResources := {
      val envProps : Map[String, String] = sys.env.map { case (k,v) => s"env.$k" -> v}
      val sysProps : Map[String, String] = sys.props.map { case (k,v) => s"sys.$k" -> v }.toMap

      ResourceFilter(
        filterSources.value,
        filterRegex.value,
        filterTargetDir.value,
        envProps ++ sysProps ++ filterProperties.value
      )(streams.value.log)
    }
  ))
}

object ResourceFilter {

  private[this] def filterCandidates(sources: File) : Seq[(File, String)] = {
    if (!sources.exists()) {
      Seq.empty
    } else {
      if (sources.isFile) {
        Seq(sources -> sources.getName)
      } else {
        val mapper : syntax.File => Option[String] = {
          f => if (f.isFile) {
            Some(f.getAbsolutePath().substring(sources.getAbsolutePath.length))
          } else {
            None
          }
        }
        PathFinder(sources).**("***").pair(mapper, false)
      }
    }
  }

  private[this] def applyFilter(
    source: File,
    pattern : Regex,
    targetDir: File,
    relative: String,
    properties: Map[String, String]
  )(implicit log: Logger) : (File, String) = {

    def performReplace(in : String) : String = {
      val replacer =  { m: Regex.Match  =>
        var variable = m.group(2)
        val matched = m.matched

        quoteReplacement(properties.getOrElse(variable, sys.error(s"Unknown variable: [$variable]")))
      }

      pattern.replaceAllIn(in, replacer)
    }

    BuildHelper.deleteRecursive(source)

    val destination = new File(targetDir, relative)
    Files.createDirectories(destination.getParentFile.toPath)

    val content = IO.read(source)
    IO.write(new File(targetDir, relative), performReplace(content))

    (destination, relative)
  }

  def apply(
    unfilteredResources : Seq[File],
    pattern: String,
    filterTargetDir : File,
    props: Map[String, String],
  )(implicit log: Logger) : Seq[(File, String)] = {
    val files = unfilteredResources.flatMap(filterCandidates)
    val regex = new Regex(pattern)
    val filtered = files.map { case (file, relative) => applyFilter(file, regex, filterTargetDir, relative, props) }
    log.info("Filtered Resources : " + filtered.mkString(","))

    filtered
  }
}
