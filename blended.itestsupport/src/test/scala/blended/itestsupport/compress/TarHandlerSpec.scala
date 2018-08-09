package blended.itestsupport.compress

import java.io.{ ByteArrayInputStream, File, InputStream }

import blended.util.logging.Logger
import org.fusesource.hawtbuf.ByteArrayOutputStream
import org.scalatest.{ FreeSpec, Matchers }

class TarHandlerSpec extends FreeSpec with Matchers {

  private[this] val log = Logger[TarHandlerSpec]

  "The Tar file handler" - {

    "should be able to read a tar file" in {

      val is : InputStream = getClass().getResourceAsStream("/test.tar")
      val content = TarFileSupport.untar(is)
      content.size should be (3)

      content should contain key ("./")
      content should contain key ("./application.conf")
      content should contain key ("./log4j.properties")
    }

    "should be able to create a tar file" in {

      import blended.testsupport.BlendedTestSupport.projectTestOutput

      val os = new ByteArrayOutputStream()
      TarFileSupport.tar(new File(s"${projectTestOutput}/data"), os)

      val content = TarFileSupport.untar(new ByteArrayInputStream(os.toByteArray()))
      content should have size(2)
    }
  }
}
