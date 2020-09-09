package blended.container.context.impl.internal

import scala.util.Try

import blended.container.context.api.ContainerContext
import blended.testsupport.BlendedTestSupport
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.updater.config.Profile
import blended.util.RichTry._
import blended.util.logging.Logger
import com.typesafe.config.Config
import org.scalatest.matchers.should.Matchers

class ContainerContextImplSpec extends LoggingFreeSpec
  with Matchers {

  private val log : Logger = Logger[ContainerContextImplSpec]

  "The container context implementation should" - {

    "initialize correctly" in {

      System.setProperty("COUNTRY", "cc")
      System.setProperty(Profile.Properties.PROFILE_PROPERTY_KEYS, "foo,bar,FOO,num,version,typeA,typeB,blended.country,blended.demoProp")
      System.setProperty("blended.home", BlendedTestSupport.projectTestOutput)
      System.setProperty("blended.container.home", BlendedTestSupport.projectTestOutput)

      val ctContext : ContainerContext = new ContainerContextImpl()

      val cfg : Config = ctContext.getConfig("ssl-config.keyManager")
      cfg.getConfig("prototype") should not be (empty)
      log.info(s"Container Context : [$ctContext]")

      val clientCfg : Config = ctContext.getConfig("akka.http.host-connection-pool")
      clientCfg should not be (empty)
      assert( Try { clientCfg.getConfig("client")}.isSuccess )

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
    }
  }
}
