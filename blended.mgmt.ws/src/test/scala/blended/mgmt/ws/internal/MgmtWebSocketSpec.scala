package blended.mgmt.ws.internal

import java.io.File

import blended.akka.http.HttpContext
import blended.akka.http.internal.BlendedAkkaHttpActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.mgmt.rest.internal.MgmtRestActivator
import blended.persistence.h2.internal.H2Activator
import blended.security.internal.SecurityActivator
import blended.security.login.impl.LoginActivator
import blended.security.login.rest.internal.RestLoginActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import org.scalatest.FreeSpec

class MgmtWebSocketSpec extends FreeSpec
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper {

  def withWebSocketServer[T](f : HttpContext => T) : T = {
    withSimpleBlendedContainer(new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()){sr =>
      withStartedBundles(sr)(Seq(
        "blended.akka" -> Some(() => new BlendedAkkaActivator()),
        "blended.akka.http" -> Some(() => new BlendedAkkaHttpActivator()),
        "blended.security" -> Some(() => new SecurityActivator()),
        "blended.security.login" -> Some(() => new LoginActivator()),
        "blended.security.login.rest" -> Some(() => new RestLoginActivator()),
        "blended.persistence.h2" -> Some(() => new H2Activator()),
        "blended.mgmt.rest" -> Some(() => new MgmtRestActivator()),
        "blended.mgmt.ws" -> Some(() => new MgmtWSActivator())
      )) { sr =>

        val ref = sr.getServiceReference(classOf[HttpContext].getName())
        val svc : HttpContext = sr.getService(ref).asInstanceOf[HttpContext]

        f(svc)
      }
    }
  }



  "The Web socket server should" - {

    "accept clients with a valid token" in {
      withWebSocketServer { ctxt =>
        pending
      }
    }
  }

}
