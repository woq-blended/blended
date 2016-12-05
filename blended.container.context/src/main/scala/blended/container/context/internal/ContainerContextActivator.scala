package blended.container.context.internal

import java.util.Properties

import blended.container.context.ContainerIdentifierService
import domino.DominoActivator
import org.slf4j.LoggerFactory
import org.slf4j.bridge.SLF4JBridgeHandler

import scala.collection.JavaConverters._
import scala.util.Try

class ContainerContextActivator extends DominoActivator {

  whenBundleActive {

    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()

    val log = LoggerFactory.getLogger(classOf[ContainerContextActivator])

    val containerContext = new ContainerContextImpl()

    val pid = classOf[ContainerIdentifierService].getPackage().getName()

    val confProps = Try { containerContext.readConfig(pid) }.getOrElse(new Properties())

    log.info(s"Container Context properties are : $confProps")

    val uuid = Option(confProps.getProperty(ContainerIdentifierServiceImpl.PROP_UUID)) match {
      case Some(x) => x.toString()
      case None => sys.error("No UUID found in configuration!")
    }

    val props = confProps.asScala.toMap.collect {
      case (k: String, v: String) if k.startsWith(ContainerIdentifierServiceImpl.PROP_PROPERTY)
        && k.length > ContainerIdentifierServiceImpl.PROP_PROPERTY.length() =>
        val realKey = k.substring(ContainerIdentifierServiceImpl.PROP_PROPERTY.length())
        log.info(s"Set identifier property [$realKey] to [$v]")
        realKey -> v
    }

    log.info(s"Container Context properties are : $props")

    (new ContainerIdentifierServiceImpl(containerContext, uuid, props)).providesService[ContainerIdentifierService]

    log.info("Container identifier is [{}]", uuid)
    log.info("Profile home directory is [{}]", containerContext.getContainerDirectory())
  }

}