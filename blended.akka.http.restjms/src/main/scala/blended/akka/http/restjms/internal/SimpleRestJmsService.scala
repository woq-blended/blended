package blended.akka.http.restjms.internal
import akka.stream.ActorMaterializer
import org.apache.camel.CamelContext

import scala.concurrent.ExecutionContext

class SimpleRestJmsService(
  override val operations : Map[String, JmsOperationConfig],
  override val camelContext: CamelContext,
  override implicit val materializer : ActorMaterializer,
  override implicit val eCtxt : ExecutionContext
) extends JMSRequestor
