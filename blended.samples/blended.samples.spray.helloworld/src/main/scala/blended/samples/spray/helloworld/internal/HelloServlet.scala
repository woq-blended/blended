package blended.samples.spray.helloworld.internal

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.apache.shiro.authc.UsernamePasswordToken
import org.apache.shiro.mgt.SecurityManager
import org.apache.shiro.subject.Subject
import org.slf4j.LoggerFactory

import blended.security.spray.ShiroBlendedSecuredRoute
import blended.spray.SprayOSGIServlet
import domino.service_consuming.ServiceConsuming
import javax.naming.AuthenticationException
import spray.routing._
import spray.routing.authentication.BasicAuth
import spray.routing.authentication.UserPass

class HelloServlet()
  extends SprayOSGIServlet
  with ServiceConsuming
  with HelloService
  with ShiroBlendedSecuredRoute 
