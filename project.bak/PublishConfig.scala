import phoenix.ProjectConfig
import sbt.Keys._
import sbt._
import xerial.sbt.Sonatype
import xerial.sbt.Sonatype.SonatypeKeys._

trait PublishConfig extends ProjectConfig {

  def publish : Boolean = true

  override def settings : Seq[sbt.Setting[_]] = super.settings ++
    (if (publish) {
      Seq(
        // General settings for subprojects to be published
        publishMavenStyle := true,
        publishArtifact in Test := false,

        sonatypeProfileName := "de.wayofquality",

        (for {
          username <- Option(System.getenv().get("SONATYPE_USERNAME"))
          password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
        } yield credentials += Credentials(
          "Sonatype Nexus Repository Manager",
          "oss.sonatype.org",
          username,
          password
        )).getOrElse(credentials ++= Seq()),

        publishTo := {
          val nexus = "https://oss.sonatype.org/"
          if (isSnapshot.value) {
            Some("snapshots" at nexus + "content/repositories/snapshots")
          } else {
            Some("releases" at nexus + "service/local/staging/deploy/maven2")
          }
        }
      )
    } else {
      Seq(
        // General settings for subprojects not to be published
        publishArtifact := false,
        Keys.publish := {},
        publishLocal := {}
      )
    })

  override def plugins : Seq[AutoPlugin] = super.plugins ++
    (if (publish) {
        Seq(Sonatype)
    } else {
      Seq()
    })

}
