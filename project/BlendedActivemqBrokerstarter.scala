object BlendedActivemqBrokerstarter
  extends ProjectSettings(
    prjName = "blended.activemq.brokerstarter",
    desc = "A simple wrapper around an Active MQ broker that makes sure that the broker is completely started before exposing a connection factory OSGi service",
    libDeps = Seq(
      Dependencies.camelJms,
      Dependencies.activeMqBroker,
      Dependencies.activeMqSpring
    )
  )
