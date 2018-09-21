import sbt._
import sbt.Keys._

object PublishConfig {
  // General settings for subprojects to be published
  lazy val doPublish = Seq(
    
    isSnapshot := version.value.endsWith("SNAPSHOT"),
    
    publishMavenStyle := true,
    publishArtifact in Test := false,
    
    (for {
      username <- Option(System.getenv().get("SONATYPE_USERNAME"))
      password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
    } yield
      credentials += Credentials(
        "Sonatype Nexus Repository Manager",
        "oss.sonatype.org",
        username,
        password)
      ).getOrElse(credentials ++= Seq()),
    
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
