// Polyglot Maven Scala file
// Shared build settings

val blendedGroupId = "de.wayofquality.blended"
val blendedVersion = "2.0-SNAPSHOT"

implicit val scalaVersion = ScalaVersion("2.10.6")


// Blended Projects

val blendedAkka = blendedGroupId % "blended.akka" % blendedVersion
val blendedPersistence = blendedGroupId % "blended.persistence" % blendedVersion
val blendedPersistenceOrient = blendedGroupId % "blended.persistence.orient" % blendedVersion
val blendedTestSupport = blendedGroupId % "blended.testsupport" % blendedVersion


// Dependencies

val domino = "com.github.domino-osgi" %% "domino" % "1.1.1"

val mockito = "org.mockito" % "mockito-all" % "1.9.5" 

val orientDbCore = "com.orientechnologies" % "orientdb-core" % "2.2.0"

val scalaTest = "org.scalatest" %% "scalatest" % "2.2.4"
val slf4jVersion = "1.7.12"
val slf4j = "org.slf4j" % "slf4j-api" % slf4jVersion
