package blended.samples.spray.helloworld.internal

import blended.spray.SprayOSGIServlet
import domino.service_consuming.ServiceConsuming

class HelloServlet()
  extends SprayOSGIServlet
  with ServiceConsuming
  with HelloService
