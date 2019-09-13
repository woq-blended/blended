package blended.akka.http.restjms.internal
import akka.stream.ActorMaterializer
import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.IdAwareConnectionFactory

import scala.concurrent.ExecutionContext

class SimpleRestJmsService(
  override val operations : Map[String, JmsOperationConfig],
  override val cf : IdAwareConnectionFactory,
  override val idService : ContainerIdentifierService,
  override implicit val materializer : ActorMaterializer,
  override implicit val eCtxt : ExecutionContext
) extends JMSRequestor
