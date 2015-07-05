package blended.container.context.internal

import blended.container.context.ContainerIdentifierService
import blended.container.context.ContainerContext
import java.util.Properties

object ContainerIdentifierServiceImpl {

  val PROP_UUID = "UUID"
  val PROP_PROPERTY = "property."

}

class ContainerIdentifierServiceImpl(containerContext: ContainerContext, uuid: String, props: Map[String, String])
    extends ContainerIdentifierService {

  override def getContainerContext(): ContainerContext = containerContext

  override def getUUID(): String = uuid

  override def getProperties(): Properties = {
    val export = new Properties()
    props.foreach {
      case (k, v) =>
        export.setProperty(k, v)
    }
    export
  }

}