package blended.testsupport.pojosr

import java.io.File

import blended.util.logging.Logger
import org.osgi.framework.{BundleActivator, ServiceReference}

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag
import scala.util.control.NonFatal

object PojoSrTestHelper {
  val OnlyOnePojoSrAtATime = new Object()
}

trait PojoSrTestHelper {

  import PojoSrTestHelper._

  def withPojoServiceRegistry[T](f: BlendedPojoRegistry => T) =
    OnlyOnePojoSrAtATime.synchronized {
      val dir = File.createTempFile("pojosr-", "")
      dir.delete()
      dir.mkdirs()
      try {
        System.setProperty("org.osgi.framework.storage", dir.getAbsolutePath())
        val registry =
          new BlendedPojoRegistry(Map("felix.cm.dir" -> dir.getAbsolutePath()))
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
        throw new RuntimeException(
          s"Could not delete ${if (file.isDirectory) "dir" else "file"}: ${file}")
      case _ =>
    }
  }

  def withStartedBundle[T](sr: BlendedPojoRegistry)(
    symbolicName: String,
    activator: Option[() => BundleActivator] = None
  )(f: BlendedPojoRegistry => T): T = {

    var bundleId: Long = 0

    try {
      bundleId = sr.startBundle(symbolicName, activator)
      f(sr)
    } catch {
      case NonFatal(e) => throw e
    } finally {
      sr.getBundleContext().getBundle(bundleId).stop()
    }
  }
  
  private[this] serviceReferences[T](sr: BlendedPojoRegistry)(filter : Option[String] = None)(implicit clazz: ClassTag[T]) :      Array[ServiceReference[T]] = Option(
    sr.getServiceReferences(
      clazz.runtimeClass.getName(), 
      filter.getOrElse(s"(objectClass=${clazz.runtimeClass.getName()})")
    )
  ).getOrElse(Array.empty).map(_.asInstanceOf[ServiceReference[T]])

  def waitOnService[T](sr: BlendedPojoRegistry)(filter : Option[String] = None)(implicit clazz: ClassTag[T], timeout : FiniteDuration) : Option[T] = {
    var result : Option[T] = None
    val start = System.currentTimeMillis()

    do {
      result = serviceReferences[T](sr)(filter).headOption
    } while (System.currentTimeMillis() - start < timeout.toMillis && result.isEmpty)

    result
  }

  def mandatoryService[T](sr: BlendedPojoRegistry)(filter: Option[String] = None)(implicit clazz : ClassTag[T], timeout: FiniteDuration) : T = {

    waitOnService[T](sr)(filter) match {
      case Some(s) => s
      case None => throw new Exception(s"Service of type [${clazz.runtimeClass.getName()}] with filter [$filter] not available. ")
    }
  }
  
  // Ensure the specified service is gone from the registry
  def ensureServiceMissing[T](sr : BlendedPojoRegistry)(filter : Option[String] = None)(implicit clazz : ClassTag[T], timeout: FiniteDuration) : Try[Unit] = {
    var result : Option[T] = None
    val start = System.currentTimeMillis()

    do {
      val refs : Array[ServiceReference[T]] = serviceReferences(sr)(filter)

      if (refs.nonEmpty) {
        result = Some(sr.getService[T](refs.head))
      }

    } while (System.currentTimeMillis() - start < timeout.toMillis && result.isDefined)

    result
  }

  def withStartedBundles[T](sr: BlendedPojoRegistry)(
      bundles: Seq[(String, Option[() => BundleActivator])]
  )(f: BlendedPojoRegistry => T): T = {

    val log = Logger[PojoSrTestHelper]
    var bundleId: Long = 0

    bundles match {
      case Seq() => f(sr)
      case head :: tail =>
        try {
          bundleId = sr.startBundle(head._1, head._2)
          withStartedBundles(sr)(tail)(f)
        } catch {
          case NonFatal(e) => throw e
        } finally {
          val bundle = sr.getBundleContext().getBundle(bundleId)
          log.info(s"Stopping bundle [${bundleId}] : ${bundle.getSymbolicName()}")
          sr.getBundleContext().getBundle(bundleId).stop()
        }
    }
  }

}
