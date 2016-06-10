import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

Model(
  "" % "blended.activemq.brokerstarter",
  packaging = "bundle",
  name = "${project.artifactId}",
  description = "A simple wrapper around an Active MQ broker that makes sure that the broker
    is completely started before exposing a connection factory OSGi service.",
  parent = Parent(
    gav = "de.wayofquality.blended" % "blended.parent" % "2.0-SNAPSHOT",
    relativePath = "../blended-parent"
  ),
  dependencies = Seq(
    "de.wayofquality.blended" % "blended.akka" % "${blended.version}",
    "de.wayofquality.blended" % "blended.jms.utils" % "${blended.version}",
    "org.apache.camel" % "camel-jms" % "${camel.version}",
    "org.apache.activemq" % "activemq-broker" % "${activemq.version}",
    "org.apache.activemq" % "activemq-spring" % "${activemq.version}"
  ),
  properties = Map(
    "bundle.symbolicName" -> "${project.artifactId}",
    "bundle.namespace" -> "${project.artifactId}"
  ),
  build = Build(
    plugins = Seq(
      Plugin(
        "org.apache.felix" % "maven-bundle-plugin"
      ),
      Plugin(
        "net.alchim31.maven" % "scala-maven-plugin"
      )
    )
  ),
  modelVersion = "4.0.0"
)
