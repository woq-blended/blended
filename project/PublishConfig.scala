import sbt._
import sbt.Keys._

object PublishConfig {
  // General settings for subprojects to be published
  lazy val doPublish = Seq(
    publishMavenStyle := true,
    publishArtifact in Test := false,
    credentials += Credentials(Path.userHome / ".sbt" / "sonatype.credentials"),
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value) {
        Some("snapshots" at nexus + "content/repositories/snapshots")
      } else {
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
      }
    }
  )

  // General settings for subprojects not to be published
  lazy val noPublish = Seq(
    publishArtifact := false,
    publishLocal := {}
  )
}
