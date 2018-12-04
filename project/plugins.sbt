

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
resolvers += Resolver.bintrayRepo("jbake", "binary")

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.0")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.25")
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.2")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")
addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.13.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.9")
//addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.1")
addSbtPlugin("com.codacy" % "sbt-codacy-coverage" % "1.3.15")

//addSbtPlugin("com.typesafe.sbt" % "sbt-osgi" % "0.9.4")
// instead we use a binary located uder project/lib containing
// https://github.com/sbt/sbt-osgi/pull/61
// but we also need to add transitive deps
libraryDependencies ++= Seq(
  "biz.aQute.bnd" % "biz.aQute.bndlib" % "4.0.0"
)

addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "1.0.0")
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")
addSbtPlugin("net.bzzt" % "sbt-reproducible-builds" % "0.16")

addSbtPlugin("de.wayofquality" % "sbt-testlogconfig" % "0.1.0-SNAPSHOT")
