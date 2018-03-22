package blended.testsupport.pojosr

import java.io.File

import org.apache.felix.connect.PojoSR
import org.apache.felix.connect.launch.PojoServiceRegistry
import org.osgi.framework.BundleActivator

import scala.collection.JavaConverters.mapAsJavaMapConverter

object PojoSrTestHelper {
  val OnlyOnePojoSrAtATime = new Object()
}

trait PojoSrTestHelper {

  import PojoSrTestHelper._

  def withPojoServiceRegistry[T](f: PojoServiceRegistry => T) = OnlyOnePojoSrAtATime.synchronized {
    val dir = File.createTempFile("pojosr-", "")
    dir.delete()
    dir.mkdirs()
    try {
      System.setProperty("org.osgi.framework.storage", dir.getAbsolutePath())
      val registry = new PojoSR(Map("felix.cm.dir" -> dir.getAbsolutePath()).asJava)
      f(registry)
    } finally {
      System.clearProperty("org.osgi.framework.storage")
      deleteRecursive(dir)
    }
  }

  private[this] def deleteRecursive(files: File*): Unit = files.map { file =>
    if (file.isDirectory) deleteRecursive(file.listFiles: _*)
    file.delete match {
      case false if file.exists =>
        throw new RuntimeException(s"Could not delete ${if (file.isDirectory) "dir" else "file"}: ${file}")
      case _ =>
    }
  }

  def withStartedBundle[T](activator: BundleActivator)(f: PojoServiceRegistry => T): T =
    withPojoServiceRegistry { sr =>
      withStartedBundle(sr)(activator)(f)
    }

  private[this] def withStartedBundle[T](sr: PojoServiceRegistry)(activator: BundleActivator)(f: PojoServiceRegistry => T): T = {
    try {
      activator.start(sr.getBundleContext())
      f(sr)
    } finally {
      activator.stop(sr.getBundleContext())
    }
  }

}