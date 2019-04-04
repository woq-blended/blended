import de.wayofquality.sbt.testlogconfig.TestLogConfig
import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import com.typesafe.sbt.osgi.SbtOsgi
import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin
import phoenix.ProjectConfig
import sbt.Keys._
import sbt.Tests.{Group, SubProcess}
import sbt._
import sbt.internal.inc.Analysis
import xerial.sbt.Sonatype
import xsbti.api.{AnalyzedClass, Projection}

trait ProjectSettings extends ProjectConfig with CommonSettings with PublishConfig {

  def osgi: Boolean = true
  def osgiDefaultImports: Boolean = true
  def description: String
  def deps: Seq[ModuleID] = Seq()

  def libDeps: Seq[ModuleID] = deps

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
      case (name, next) => name + next.capitalize
    }
    Project(name, file(projectDir.getOrElse(projectName)))
  }

  def bundle: BlendedBundle = BlendedBundle(
    bundleSymbolicName = projectName,
    exportPackage = Seq(projectName),
    privatePackage = Seq(s"${projectName}.internal.*"),
    defaultImports = osgiDefaultImports
  )

  def sbtBundle: Option[BlendedBundle] = if (osgi) Some(bundle) else None

  private def hasForkAnnotation(clazz: AnalyzedClass): Boolean = {

    val c = clazz.api().classApi()

    c.annotations.exists { ann =>
      ann.base() match {
        case proj: Projection if proj.id() == "RequiresForkedJVM" => true
        case _ => false
      }
    }
  }

  def defaultSettings: Seq[Setting[_]] = {

    val osgiSettings: Seq[Setting[_]] = sbtBundle.toSeq.flatMap(_.osgiSettings)

    Seq(
      Keys.name := projectName,
      Keys.moduleName := Keys.name.value,
      Keys.description := description,
      Keys.libraryDependencies ++= libDeps,
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

        val combined : Tests.Group = new Group(
          name = "Combined",
          tests = otherTests,
          runPolicy = SubProcess(config = ForkOptions.apply().withRunJVMOptions(options))
        )

        val forked : Seq[Tests.Group] = forkedTests.map { t =>
          new Group(
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

    ) ++ osgiSettings ++ (
        // We need to explicitly load the rb settings again to
        // make sure the OSGi package is post-processed:
        ReproducibleBuildsPlugin.projectSettings
      )
  }

  override def settings: Seq[sbt.Setting[_]] = super.settings ++ defaultSettings

 override def plugins: Seq[AutoPlugin] = super.plugins ++
    Seq(ReproducibleBuildsPlugin) ++
    Seq(TestLogConfig) ++
    (if (osgi) Seq(SbtOsgi) else Seq())

}
