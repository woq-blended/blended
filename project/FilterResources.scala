import java.nio.file.Files

import sbt.Keys._
import sbt._
import sbt.io.syntax

import scala.util.matching.Regex
import scala.util.matching.Regex.quoteReplacement

object FilterResources extends AutoPlugin {

  object autoImport {
    val filterSources = settingKey[Seq[File]]("Resource to filter (files and directories supported)")
    val filterTargetDir = settingKey[File]("Target directory for the filtered files")
    val filterResources = taskKey[Seq[(File, String)]]("Filter the unfiltered resources")
    val filterProperties = settingKey[Map[String, String]]("Extra properties to be applied while filtering")
    val filterRegex = settingKey[String]("The replacement pattern. The actual lookup-key must be matched by regex group 1.")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] =
    inConfig(Compile)(Seq(
      filterProperties := Map.empty,
      filterSources := Seq(baseDirectory.value / "src" / "main" / "filterResources"),
      filterTargetDir := classDirectory.value,
      filterRegex := """\$\{(.+?)\}""",
      filterResources := {
        val envProps: Map[String, String] = sys.env.map { case (k, v) => s"env.$k" -> v }
        val sysProps: Map[String, String] = sys.props.map { case (k, v) => s"sys.$k" -> v }.toMap

        ResourceFilter(
          filterSources.value,
          filterRegex.value,
          filterTargetDir.value,
          envProps ++ sysProps ++ filterProperties.value
        )(streams.value.log)
      },
      exportedProducts := {
        // exec before exportedProducts
        filterResources.value
        exportedProducts.value
      }
    )) ++
      inConfig(Test)(Seq(
        filterProperties := Map.empty,
        filterSources := Seq(baseDirectory.value / "src" / "test" / "filterResources"),
        filterTargetDir := classDirectory.value,
        filterRegex := """\$\{(.+?)\}""",
        filterResources := {
          val envProps: Map[String, String] = sys.env.map { case (k, v) => s"env.$k" -> v }
          val sysProps: Map[String, String] = sys.props.map { case (k, v) => s"sys.$k" -> v }.toMap

          ResourceFilter(
            filterSources.value,
            filterRegex.value,
            filterTargetDir.value,
            envProps ++ sysProps ++ filterProperties.value
          )(streams.value.log)
        },
        exportedProducts := {
          // exec before exportedProducts
          filterResources.value
          exportedProducts.value
        }
      ))
}

object ResourceFilter {

  private[this] def filterCandidates(sources: File): Seq[(File, String)] = {
    if (!sources.exists()) {
      Seq.empty
    } else {
      if (sources.isFile) {
        Seq(sources -> sources.getName)
      } else {
        val mapper: syntax.File => Option[String] = {
          f =>
            if (f.isFile) {
              Some(f.getAbsolutePath().substring(sources.getAbsolutePath().length + 1))
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
    pattern: Regex,
    targetDir: File,
    relative: String,
    properties: Map[String, String]
  )(implicit log: Logger): (File, String) = {

    def performReplace(in: String): String = {
      val replacer = { m: Regex.Match =>
        var variable = m.group(1)
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
    unfilteredResources: Seq[File],
    pattern: String,
    filterTargetDir: File,
    props: Map[String, String]
  )(implicit log: Logger): Seq[(File, String)] = {
    val files = unfilteredResources.flatMap(filterCandidates)
    val regex = new Regex(pattern)
    val filtered = files.map { case (file, relative) => applyFilter(file, regex, filterTargetDir, relative, props) }
    log.debug("Filtered Resources : " + filtered.mkString(","))

    filtered
  }
}
