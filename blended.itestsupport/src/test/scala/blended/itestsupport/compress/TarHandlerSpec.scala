package blended.itestsupport.compress

import java.io.InputStream

import org.scalatest.{FreeSpec, Matchers}
import org.slf4j.LoggerFactory

class TarHandlerSpec extends FreeSpec with Matchers {

  private[this] val log = LoggerFactory.getLogger(classOf[TarHandlerSpec])

  "The Tar file handler" - {

    "should be able to read a tar file" in {

      val is : InputStream = getClass().getResourceAsStream("/test.tar")
      val content = TarFileSupport.untar(is)
      content.size should be (3)

      content should contain key ("./")
      content should contain key ("./application.conf")
      content should contain key ("./log4j.properties")
    }
  }
}
