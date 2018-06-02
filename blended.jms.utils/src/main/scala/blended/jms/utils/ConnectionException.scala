package blended.jms.utils

import javax.jms.JMSException

case class ConnectionException(vendor: String, provider: String, e: JMSException)
