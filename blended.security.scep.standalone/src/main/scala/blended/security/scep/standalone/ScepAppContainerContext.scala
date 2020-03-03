package blended.security.scep.standalone

import java.io.File
import java.util.Properties

import blended.container.context.impl.internal.AbstractContainerContextImpl
import blended.util.logging.Logger
import com.typesafe.config.impl.Parseable
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions}

import scala.beans.BeanProperty

class ScepAppContainerContext(baseDir : String) extends AbstractContainerContextImpl {

  private[this] val log = Logger[this.type]
  log.debug(s"Starting [$this]")

  private val SECRET_FILE_PATH : String = "blended.security.secretFile"

  @BeanProperty override lazy val containerDirectory : String = baseDir

  @BeanProperty override lazy val containerConfigDirectory : String = containerDirectory + "/etc"

  @BeanProperty override lazy val containerLogDirectory : String = baseDir

  @BeanProperty override lazy val profileDirectory : String = containerDirectory

  @BeanProperty override lazy val profileConfigDirectory : String = containerConfigDirectory

  @BeanProperty override lazy val containerHostname : String = "localhost"

  @BeanProperty override lazy val containerConfig : Config = {
    val sys = new Properties()
    sys.putAll(System.getProperties())
    val sysProps = Parseable.newProperties(sys, ConfigParseOptions.defaults().setOriginDescription("system properties")).parse()
    val envProps = ConfigFactory.systemEnvironment()

    ConfigFactory.parseFile(
      new File(profileConfigDirectory, "application.conf"),
      ConfigParseOptions.defaults().setAllowMissing(false)
    ).
      withFallback(sysProps).
      withFallback(envProps).
      withFallback(ConfigFactory.parseResources(getClass().getClassLoader(), "application-defaults.conf")).
      resolve()
  }
}
