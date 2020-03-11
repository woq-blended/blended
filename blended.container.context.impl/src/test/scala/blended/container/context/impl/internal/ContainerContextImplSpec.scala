package blended.container.context.impl.internal

import blended.container.context.api.ContainerContext
import blended.testsupport.BlendedTestSupport
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.updater.config.RuntimeConfig
import blended.util.logging.Logger
import org.scalatest.Matchers
import blended.util.RichTry._
import com.typesafe.config.Config

import scala.collection.JavaConverters._

class ContainerContextImplSpec extends LoggingFreeSpec
  with Matchers {

  private val log : Logger = Logger[ContainerContextImplSpec]

  "The container context implementation should" - {

    "initialize correctly" in {

      System.setProperty("COUNTRY", "cc")
      System.setProperty(RuntimeConfig.Properties.PROFILE_PROPERTY_KEYS, "foo,bar,FOO,num,version,typeA,typeB,blended.country,blended.demoProp")
      System.setProperty("blended.home", BlendedTestSupport.projectTestOutput)
      System.setProperty("blended.container.home", BlendedTestSupport.projectTestOutput)

      val ctContext : ContainerContext = new ContainerContextImpl()

      log.info(s"Container Context : [$ctContext]")

      ctContext.properties should have size(9)
      ctContext.properties.get("foo") should be (Some("bar"))
      ctContext.properties.get("bar") should be (Some("test"))
      ctContext.properties.get("blended.country") should be (Some("cc"))

      ctContext.containerDirectory should be (BlendedTestSupport.projectTestOutput)
      ctContext.uuid should be ("context")

      ctContext.resolveString("$[[" + ContainerContext.containerId + "]]").unwrap should be(ctContext.uuid)

      ctContext.containerConfig.entrySet() should not be (empty)

      ctContext.containerConfig.getString("blended.sample") should be ("cc")
      ctContext.containerConfig.getString("blended.persistence.h2.dbUserName") should be ("admin")

      ctContext.containerConfig.getConfig("akka.http") should not be (empty)
      ctContext.containerConfig.getConfig("akka.stream") should not be (empty)

      val cfg : Config = ctContext.containerConfig.getConfig("akka.ssl-config")
      cfg should not be (empty)

      println(cfg.entrySet().asScala.mkString("\n"))
    }
  }

}
