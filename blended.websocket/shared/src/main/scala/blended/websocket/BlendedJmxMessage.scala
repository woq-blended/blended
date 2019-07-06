package blended.websocket

import blended.jmx.{JmxBeanInfo, JmxObjectName}

sealed trait  BlendedJmxMessage extends WithKey

/**
  * WebSocket clients use this message to subscribe to Jmx updates.
  *
  * A subscription will always receive the list of registered object
  * names and changes to that list. If the optional parameter objName
  * is set, the objName the behavior is as follows:
  *
  * If an MBean instance with the given object name is found, the update
  * will contain updates only for this instance.
  *
  * If no MBean instance can be found for the given name, the name will
  * be used as a pattern to search for MBeans and all direct children
  * of the given name will be included in the update.
  *
  * A direct child in that sense is an MBean that extends the attribute
  * of the given name by exactly one attribute.
  *
  * Subsequent subscribe messages will replace an existing subscription.
  *
  * The command handler will emit a subscription update as specified
  * in the interval. If the interval is <= 0, the command handler
  * will only emit one update and then remove the subscription.
  *
  * @param objName An optional object name to restrict the subscription
  */
case class JmxSubscribe(
  objName: Option[JmxObjectName],
  intervalMS: Long,
) extends BlendedJmxMessage {
  override def key: String = objName.map(_.objectName).getOrElse("None")
}

/**
  * WebSocket clients will use this message to cancel their Jmx subscription.
  */
case class JmxUnsubscribe(sub : JmxSubscribe) extends BlendedJmxMessage {
  override def key: String = sub.key
}

/**
  * The command handler will send JmxUpdated to it's subscribers.
  * @param names - The names of all(!!) registered MBeans.
  * @param beans - Additional MBean Info, if applicable
  */
case class JmxUpdate(
  names : Seq[JmxObjectName],
  beans : Seq[JmxBeanInfo]
) extends BlendedJmxMessage
