import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedJmsSampler extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name

    override val projectName : String = "blended.jms.sampler"
    override val description : String = "A bundle to sample messages from a given JMS topic."

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.geronimoJms11Spec
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      exportPackage = Seq.empty,
      bundleActivator = s"$projectName.internal.JmsSamplerActivator"
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedAkka.project,
      BlendedUtil.project
    )
  }
}
