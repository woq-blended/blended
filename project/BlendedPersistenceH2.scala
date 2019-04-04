import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedPersistenceH2 extends ProjectFactory {
  object config extends ProjectSettings {

    override val projectName = "blended.persistence.h2"
    override val description = "Implement a persistence backend with H2 JDBC database"

    override def deps = Seq(
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

    override def bundle: BlendedBundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.H2Activator",
      exportPackage = Seq(),
      privatePackage = Seq(
        s"${projectName}.internal",
        "blended.persistence.jdbc"
      )
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedPersistence.project,
      BlendedUtilLogging.project,
      BlendedUtil.project,
      BlendedTestsupport.project % Test,
      // we want to use the scalacheck data generators in tests
      BlendedUpdaterConfigJvm.project % "test->test"
    )
  }
}
