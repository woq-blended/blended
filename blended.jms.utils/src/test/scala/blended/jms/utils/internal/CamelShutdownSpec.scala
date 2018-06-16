package blended.jms.utils.internal

import java.util.Date

import akka.actor.ActorSystem
import akka.testkit.TestKit
import blended.camel.utils.BlendedCamelContextFactory
import blended.jms.utils.{AmqBrokerSupport, BlendedJMSConnectionConfig, BlendedSingleConnectionFactory, ConnectionException}
import javax.jms.{ExceptionListener, JMSException}
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.jms.JmsComponent
import org.apache.camel.impl.SimpleRegistry
import org.apache.camel.{Exchange, Processor}
import org.scalatest.FreeSpecLike
import org.slf4j.LoggerFactory

class CamelShutdownSpec extends TestKit(ActorSystem("CamelShutdown"))
  with FreeSpecLike
  with AmqBrokerSupport {

  override lazy val brokerName: String = "shutdown"

  val log = LoggerFactory.getLogger(classOf[CamelShutdownSpec])

  "A Camel Context with an JMS endpoint should" - {

    "shutdown in a timely fashion even if the underlying connection factory has been stopped" in {

      val broker = startBroker()

      val cfg = BlendedJMSConnectionConfig.defaultConfig.copy(
        vendor = "amq",
        provider = "amq",
        clientId = "camelShutdown",
        jmxEnabled = false,
        cfClassName = Some(classOf[ActiveMQConnectionFactory].getName),
        jmsClassloader = Some(getClass().getClassLoader()),
        cfEnabled = Some( _ => true),
        properties = Map( "brokerURL" -> amqCf().getBrokerURL())
      )

      val cf = new BlendedSingleConnectionFactory(cfg, system, None)

      val exceptionHandler : Exception => Unit = { e =>

        def getJmsCause(current : Throwable) : Option[JMSException] = current match {
          case jmse : JMSException => Some(jmse)
          case o => Option(o) match {
            case None => None
            case Some(same) if same == same.getCause() => None
            case Some(e) => getJmsCause(e.getCause())
          }
        }

        getJmsCause(e) match {
          case None =>
          case Some(illegalState) if illegalState.isInstanceOf[javax.jms.IllegalStateException] =>
            system.eventStream.publish(ConnectionException("amq", "amq", illegalState))
          case jmse =>
        }

      }

      val exceptionListener = new ExceptionListener {
        override def onException(exception: JMSException): Unit = exceptionHandler(exception)
      }

      val camelCtxt = BlendedCamelContextFactory.createContext("CamelShutdown", false)
      camelCtxt.addComponent("jms", JmsComponent.jmsComponent(cf))
      camelCtxt.getRegistry(classOf[SimpleRegistry]).put("el", exceptionListener)

      val consumerSettings = "acknowledgementModeName=CLIENT_ACKNOWLEDGE&cacheLevelName=CACHE_NONE&exceptionListener=#el&receiveTimeout=300000"
      camelCtxt.addRoutes(new RouteBuilder() {
        override def configure(): Unit = {

          onException(classOf[Exception]).to("jms:error")

          0.to(20).foreach { i =>
            from(s"jms:SampleQ1$i?$consumerSettings").id(s"R$i").to(s"jms:SampleQ2$i")

            from(s"scheduler://foo$i?delay=100")
              .process(new Processor {
                override def process(exchange: Exchange): Unit = {
                  exchange.getOut().setBody(new Date().toString())
                }
              })
              .to(s"jms:SampleQ1$i?deliveryMode=2")

            from(s"jms:SampleQ2$i?$consumerSettings").log(s"${body}")
          }
        }
      })

      camelCtxt.start()

      Thread.sleep(3000)

      stopBroker(broker)
      Thread.sleep(3000)

      val start = System.currentTimeMillis()

      new Thread(new Runnable {
        override def run(): Unit = {
          camelCtxt.removeRoute("R16")
        }

      }).start()

      new Thread(new Runnable {
        override def run(): Unit = {
          LoggerFactory.getLogger("CamelStopper").info("Stopping Camel Context ...")
          camelCtxt.stop()
        }
      }).start()

      var stopped = false

      while(!stopped && System.currentTimeMillis() - start < 30000) {
        stopped = camelCtxt.getStatus().isStopped()
        if (!stopped) {
          log.info("Waiting for Camel Context to stop properly")
          Thread.sleep(500)
        }
      }

      val stopTimeMs = System.currentTimeMillis() - start
      log.info(s"Camel Context stopped in [$stopTimeMs]ms")
      assert(camelCtxt.getStatus().isStopped())
      assert(stopTimeMs < 10000)
    }
  }

}
