// Plain versions

val activeMqVersion = "5.13.3"
val akkaVersion = "2.3.10"
val camelVersion = "2.16.3"

// Dependencies

val activeMqBroker = "org.apache.activemq" % "activemq-broker" % activeMqVersion
val activeMqSpring = "org.apache.activemq" % "activemq-spring" % activeMqVersion
val activeMqOsgi = "org.apache.activemq" % "activemq-osgi" % activeMqVersion

val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
val akkaOsgi = "com.typesafe.akka" %% "akka-osgi" % akkaVersion
val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion
val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion

val apacheShiroVersion = "1.2.4"
val apacheShiroCore = "org.apache.shiro" % "shiro-core" % apacheShiroVersion
val apacheShiroWeb = "org.apache.shiro" % "shiro-web" % apacheShiroVersion

val bndLib = "biz.aQute.bnd" % "biz.aQute.bndlib" % "3.2.0"

val camelJms = "org.apache.camel" % "camel-jms" % camelVersion

val domino = "com.github.domino-osgi" %% "domino" % "1.1.1"

val felixFramework = "org.apache.felix" % "org.apache.felix.framework" % "5.0.0"
val felixFileinstall = "org.apache.felix" % "org.apache.felix.fileinstall" % "3.4.2"
val felixGogoCommand = "org.apache.felix" % "org.apache.felix.gogo.command" % "0.14.0"
val felixGogoShell = "org.apache.felix" % "org.apache.felix.gogo.shell" % "0.10.0"
val felixGogoRuntime = "org.apache.felix" % "org.apache.felix.gogo.runtime" % "0.16.2"

val jms11Spec = "org.apache.geronimo.specs" % "geronimo-jms_1.1_spec" % "1.1.1"
val junit = "junit" % "junit" % "4.11"

val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.1.3"

val mockitoAll = "org.mockito" % "mockito-all" % "1.9.5"

val orientDbCore = "com.orientechnologies" % "orientdb-core" % "2.2.0"

val scalaLib = "org.scala-lang" % "scala-library" % scalaVersion.version
val scalaReflect = "org.scala-lang" % "scala-reflect" % scalaVersion.version
val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.0.5"
val scalaTest = "org.scalatest" %% "scalatest" % "2.2.4"
val slf4jVersion = "1.7.12"
val slf4j = "org.slf4j" % "slf4j-api" % slf4jVersion
val slf4jLog4j12 = "org.slf4j" % "slf4j-log4j12" % slf4jVersion

val orgOsgi = "org.osgi" % "org.osgi.core" % "5.0.0"
val orgOsgiCompendium = "org.osgi" % "org.osgi.compendium" % "5.0.0"

val typesafeConfig = "com.typesafe" % "config" % "1.2.1"
