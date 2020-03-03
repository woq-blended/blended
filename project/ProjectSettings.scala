import blended.sbt.phoenix.osgi.OsgiConfig
import de.wayofquality.sbt.testlogconfig.TestLogConfig
import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin
import phoenix.ProjectConfig
import sbt.Keys._
import sbt.Tests.{Group, SubProcess}
import sbt._
import sbt.internal.inc.Analysis
import xsbti.api.{AnalyzedClass, Projection}

trait ProjectSettings
  extends ProjectConfig
  with CommonSettings
  with PublishConfig
  with OsgiConfig {

  /** The project descriptions. Also used in published pom.xml and as bundle description. */
  def description: String
  /** Dependencies */
  def deps: Seq[ModuleID] = Seq()

  /**
   * Override this method to specify additional plugins for this project.
   */
  def extraPlugins: Seq[AutoPlugin] = Seq.empty

  /**
   * Override this method to customize the creation of this project.
   */
  def projectFactory: () => Project = { () =>
    val name = projectName.split("[.]").foldLeft("") {
      case ("", next) => next
      case (n, next) => n + next.capitalize
    }
    Project(name, file(projectDir.getOrElse(projectName)))
  }
  
  private def hasForkAnnotation(clazz: AnalyzedClass): Boolean = {

    val c = clazz.api().classApi()

    c.annotations.exists { ann =>
      ann.base() match {
        case proj: Projection if proj.id() == "RequiresForkedJVM" => true
        case _ => false
      }
    }
  }

  override def settings: Seq[sbt.Setting[_]] = super.settings ++ {

    Seq(
      scalaVersion := "2.12.8",
      Keys.name := projectName,
      Keys.moduleName := Keys.name.value,
      Keys.description := description,
      Keys.libraryDependencies ++= deps,
      Test / javaOptions += ("-DprojectTestOutput=" + (Test / classDirectory).value),
      Test / fork := true,
      Test / parallelExecution := false,
      Compile / unmanagedResourceDirectories += baseDirectory.value / "src" / "main" / "binaryResources",
      Test / unmanagedResourceDirectories += baseDirectory.value / "src" / "test" / "binaryResources",

      Test / testlogDirectory := (Global/testlogDirectory).value,
      Test / testlogLogToConsole := false,
      Test / testlogLogToFile := true,

      Test / resourceGenerators += (Test / testlogCreateConfig).taskValue,

      // inspired by : https://chariotsolutions.com/blog/post/sbt-group-annotated-tests-run-forked-jvms
      Test / testGrouping := {

        val log = streams.value.log

        val options = (Test/javaOptions).value.toVector

        val annotatedTestNames : Seq[String] = (Test/compile).value.asInstanceOf[Analysis]
          .apis.internal.values.filter(hasForkAnnotation).map(_.name()).toSeq

        val (forkedTests, otherTests) = (Test / definedTests).value.partition{ t =>
          annotatedTestNames.contains(t.name)
        }

        val combined : Tests.Group = Group(
          name = "Combined",
          tests = otherTests,
          runPolicy = SubProcess(config = ForkOptions.apply().withRunJVMOptions(options))
        )

        val forked : Seq[Tests.Group] = forkedTests.map { t =>
          Group(
            name = t.name,
            tests = Seq(t),
            runPolicy = SubProcess(config = ForkOptions.apply().withRunJVMOptions(options))
          )
        }

        if (forkedTests.nonEmpty) {
          log.info(s"Forking extra JVM for test [${annotatedTestNames.mkString(",")}]")
        }

        forked ++ Seq(combined)
      },

    ) ++
      // We need to explicitly load the rb settings again to
      // make sure the OSGi package is post-processed:
      ReproducibleBuildsPlugin.projectSettings

  }


  override def plugins: Seq[AutoPlugin] = super.plugins ++
    Seq(ReproducibleBuildsPlugin) ++
    Seq(TestLogConfig)

}
