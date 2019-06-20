import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedPersistenceH2 extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.persistence.h2"
    override val description : String = "Implement a persistence backend with H2 JDBC database"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.slf4j,
      Dependencies.domino,
      Dependencies.h2,
      Dependencies.hikaricp,
      Dependencies.springBeans,
      Dependencies.springCore,
      Dependencies.springTx,
      Dependencies.springJdbc,
      Dependencies.liquibase,
      Dependencies.snakeyaml,
      Dependencies.jclOverSlf4j % Test,
      Dependencies.scalatest % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.lambdaTest % Test,
      Dependencies.scalacheck % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.H2Activator",
      exportPackage = Seq(),
      privatePackage = Seq(
        s"$projectName.internal",
        "blended.persistence.jdbc"
      )
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedPersistence.project,
      BlendedUtilLogging.project,
      BlendedUtil.project,
      BlendedTestsupport.project % Test,
      // we want to use the scalacheck data generators in tests
      BlendedUpdaterConfigJvm.project % "test->test"
    )
  }
}
