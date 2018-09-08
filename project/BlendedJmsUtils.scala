object BlendedJmsUtils
extends ProjectSettings(
  prjName = "blended.jms.utils",
  desc = "A bundle to provide a ConnectionFactory wrapper that monitors a single connection and is able to monitor the connection via an active ping.",
  libDeps = Seq(
    Dependencies.camelJms,
    Dependencies.jms11Spec,
    Dependencies.scalatest % "test",
    Dependencies.akkaSlf4j % "test",
    Dependencies.akkaStream % "test",
    Dependencies.activeMqBroker % "test",
    Dependencies.activeMqKahadbStore % "test",
    Dependencies.akkaTestkit % "test",
    Dependencies.logbackCore % "test",
    Dependencies.logbackClassic % "test"
  )
)
