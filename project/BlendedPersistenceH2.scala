import sbt._

object BlendedPersistenceH2 extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.persistence.h2",
    description = "Implement a persistence backend with H2 JDBC database",
    deps = Seq(
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
      Dependencies.jclOverSlf4j % "test",
      Dependencies.scalatest % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.lambdaTest % "test",
      Dependencies.scalacheck % "test"
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.H2Activator",
      exportPackage = Seq(),
      privatePackage = Seq(
        s"${b.bundleSymbolicName}.internal",
        "blended.persistence.jdbc"
      )
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedPersistence.project,
    BlendedUtilLogging.project,
    BlendedUtil.project,
    BlendedTestsupport.project % "test",
    // we want to use the scalacheck data generators in tests
    BlendedUpdaterConfigJvm.project % "test->test"
  )
}
