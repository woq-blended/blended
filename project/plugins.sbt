

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
resolvers += Resolver.bintrayRepo("jbake", "binary")

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.0")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.25")
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.2")
// Generate test coverage reports
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")
addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.13.1")
// Build assembly artifacts (zip,gz,tgz)
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.9")
//addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")
// Support for artifact signing with pgp
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.1")
// Export test coveragt to codacy
addSbtPlugin("com.codacy" % "sbt-codacy-coverage" % "1.3.15")

// OSGi bundle build support
addSbtPlugin("com.typesafe.sbt" % "sbt-osgi" % "0.9.5")

// bloop compile server support (config file generator currently)
addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "1.0.0")
// Scala source code formatter (also used by Scala-IDE/Eclipse)
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")
// Strip artifacts to make them more reproducible (same input -> same output, checksum wise)
addSbtPlugin("net.bzzt" % "sbt-reproducible-builds" % "0.16")

// Generate logging config for test execution
addSbtPlugin("de.wayofquality" % "sbt-testlogconfig" % "0.1.0-SNAPSHOT")

// Generate site with JBake
addSbtPlugin("de.wayofquality" % "sbt-jbake" % "0.1.0-SNAPSHOT")

// Filter resources (like Maven)
addSbtPlugin("de.wayofquality" % "sbt-filterresources" % "0.1.1-SNAPSHOT")
