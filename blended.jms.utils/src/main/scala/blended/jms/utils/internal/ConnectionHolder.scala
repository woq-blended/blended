package blended.jms.utils.internal

import java.io.{PrintWriter, StringWriter}
import java.util
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import blended.jms.utils.{BlendedJMSConnection, BlendedJMSConnectionConfig}
import blended.util.ReflectionHelper
import javax.jms.{Connection, ConnectionFactory, ExceptionListener, JMSException}
import javax.naming.{Context, InitialContext}
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

case class ConnectionHolder(
  config : BlendedJMSConnectionConfig,
  system : ActorSystem
) {

  lazy val vendor : String = config.vendor
  lazy val provider : String = config.provider

  private[this] val log = LoggerFactory.getLogger(classOf[ConnectionHolder])
  private[this] var conn : Option[BlendedJMSConnection] = None

  private[this] var connecting : AtomicBoolean = new AtomicBoolean(false)

  private[this] val initialContextEnv : util.Hashtable[String, Object] = {
    val envMap = new util.Hashtable[String, Object]()

    val cfgMap : Map[String, String] =
      config.properties ++ (config.ctxtClassName.map( c => (Context.INITIAL_CONTEXT_FACTORY -> c) ).toMap)

    cfgMap.foreach { case (k,v) =>
      envMap.put(k,v)
    }

    log.info(s"Initial context properties [${cfgMap.mkString(", ")}]")

    envMap
  }

  private[this] def lookupConnectionFactory() : ConnectionFactory = {

    val oldLoader = Thread.currentThread().getContextClassLoader()

    var context : Option[Context] = None

    try {

      val (name, contextFactoryClass) = (config.jndiName, config.ctxtClassName) match {
        case (Some(n), Some(c)) => (n,c)
        case (_, _) =>
          throw new JMSException(s"Context Factory class and JNDI name have to be defined for JNDI lookup [$vendor:$provider].")
       }

      config.jmsClassloader.foreach(Thread.currentThread().setContextClassLoader)

      context = Some(new InitialContext(initialContextEnv))
      log.info(s"Looking up JNDI name [$name]")
      context.get.lookup(name).asInstanceOf[ConnectionFactory]
    } catch {
      case NonFatal(t) =>
        val sw = new StringWriter()
        t.printStackTrace(new PrintWriter(sw))
        log.warn(s"Could not lookup ConnectionFactory : [${t.getMessage()}]")
        log.error(sw.toString)
        val ex : JMSException = new JMSException("Could not lookup ConnectionFactory")
        throw ex
    } finally {
      context.foreach{ c =>
        log.info(s"Closing Initial Context Factory [${config.ctxtClassName}] : [${config.jndiName}]")
        c.close()
      }
      Thread.currentThread().setContextClassLoader(oldLoader)
    }
  }

  private[this] def createConnectionFactory() : ConnectionFactory = {

    val oldLoader = Thread.currentThread().getContextClassLoader()

    try {
      val cf : ConnectionFactory = config.cfClassName match {
        case None => throw new Exception(s"Connection Factory class must be specified for [$vendor:$provider]")
        case Some(c) =>

          config.jmsClassloader.foreach(Thread.currentThread().setContextClassLoader)

          log.info(s"Configuring connection factory of type [$c].")
          Thread.currentThread().getContextClassLoader().loadClass(c).newInstance().asInstanceOf[ConnectionFactory]
      }

      config.properties.foreach { case (k, v) =>
        log.info(s"Setting property [$k] for connection factory [$vendor:$provider] to [$v].")
        ReflectionHelper.setProperty(cf, v, k)
      }

      cf
    } catch {
      case NonFatal(t) =>
        log.warn(s"Could not create ConnectionFactory : [${t.getMessage()}]")
        val ex : JMSException = new JMSException("Could not create ConnectionFactory")
        throw ex
    } finally {
      Thread.currentThread().setContextClassLoader(oldLoader)
    }
  }

  def getConnection() : Option[BlendedJMSConnection] = conn

  def connect() : Connection = conn match {
    case Some(c) => c
    case None =>

      if (!connecting.getAndSet(true)) {
        try {
          log.info(s"Creating underlying connection for provider [$vendor:$provider] with client id [${config.clientId}]")

          val cf : ConnectionFactory = if (config.useJndi) lookupConnectionFactory() else createConnectionFactory()

          val c = config.defaultUser match {
            case None => cf.createConnection()
            case Some(user) => cf.createConnection(user, config.defaultPassword.getOrElse(null))
          }

          try {
            c.setClientID(config.clientId)

            c.setExceptionListener(new ExceptionListener {
              override def onException(e: JMSException): Unit = {
                log.warn(s"Exception encountered in connection for provider [$vendor:$provider] : ${e.getMessage()}")
                system.eventStream.publish(ConnectionException(vendor, provider, e))
              }
            })
          } catch {
            case NonFatal(e) =>
              log.error(s"Error setting client Id [${config.clientId}]...Closing Connection...")
              c.close()
              throw e
          }

          c.start()

          log.info(s"Successfully connected to [$vendor:$provider] with clientId [${config.clientId}]")
          val wrappedConnection = new BlendedJMSConnection(c)
          conn = Some(wrappedConnection)

          wrappedConnection
        } catch {
          case e : JMSException =>
            log.warn(s"Error creating connection [$vendor:$provider] : [${e.getMessage()}] ")
            throw e
        } finally {
          connecting.set(false)
        }

      } else {
        throw new JMSException(s"Connection Factory for provider [$provider] is still connecting.")
      }
  }

  def close() : Unit = {
    log.info(s"Closing underlying connection for provider [$provider]")
    conn.foreach(_.connection.close())
    conn = None
  }
}
