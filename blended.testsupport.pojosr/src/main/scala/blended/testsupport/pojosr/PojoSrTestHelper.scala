package blended.testsupport.pojosr

import java.io.File

import org.apache.felix.connect.launch.PojoServiceRegistry
import org.osgi.framework.BundleActivator

object PojoSrTestHelper {
  val OnlyOnePojoSrAtATime = new Object()
}

trait PojoSrTestHelper {

  import PojoSrTestHelper._

  def withPojoServiceRegistry[T](f: BlendedPojoRegistry => T) = OnlyOnePojoSrAtATime.synchronized {
    val dir = File.createTempFile("pojosr-", "")
    dir.delete()
    dir.mkdirs()
    try {
      System.setProperty("org.osgi.framework.storage", dir.getAbsolutePath())
      val registry = new BlendedPojoRegistry(Map("felix.cm.dir" -> dir.getAbsolutePath()))
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

  def withStartedBundle[T](sr: BlendedPojoRegistry)(
    symbolicName : String, activator: Option[() => BundleActivator] = None
  )(f: BlendedPojoRegistry => T): T = {

    var bundleId : Long = 0

    try {
      bundleId = sr.startBundle(symbolicName, activator)
      f(sr)
    } catch {
      case t : Throwable =>
        println(t.getStackTrace())
        throw t
    } finally {
      sr.getBundleContext().getBundle(bundleId).stop()
    }
  }

}