package blended.testsupport.pojosr

import java.io.File
import java.util.UUID

import blended.container.context.api.ContainerContext
import blended.util.logging.Logger
import domino.DominoActivator
import org.osgi.framework.{BundleActivator, ServiceReference}

import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.Try
import scala.util.control.NonFatal

object PojoSrTestHelper {
  val OnlyOnePojoSrAtATime = new Object()
}

class MandatoryServiceUnavailable(clazz : Class[_], filter : Option[String]) extends Exception(s"Service of type [${clazz.getName()}] with filter [$filter] not available.")

trait PojoSrTestHelper {

  private val log = Logger[PojoSrTestHelper]

  import PojoSrTestHelper._

  def baseDir : String
  def pojoUuid : String = "simple"

  private def propertyStorage = "org.osgi.framework.storage"

  def createRegistry() : Try[BlendedPojoRegistry] = {
    val dir = File.createTempFile("pojosr-", "")
    dir.delete()
    dir.mkdirs()
    Try {
      OnlyOnePojoSrAtATime.synchronized {
        System.setProperty(propertyStorage, dir.getAbsolutePath())
        new BlendedPojoRegistry(Map("felix.cm.dir" -> dir.getAbsolutePath()))
      }
    }
  }

  def contextActivator(
    mandatoryProperties : Option[String] = None
  ) : BundleActivator = {
    new DominoActivator {

      mandatoryProperties.foreach(s =>
        System.setProperty("blended.updater.profile.properties.keys", s))

      whenBundleActive {
        new MockContainerContext(baseDir, pojoUuid).providesService[ContainerContext]
      }
    }
  }

  def withPojoServiceRegistry[T](f : BlendedPojoRegistry => Try[T]) : Try[T] = Try {

    val registry = createRegistry().get
    val result = f(registry).get
    val dir = new File(System.getProperty(propertyStorage))
    System.clearProperty(propertyStorage)
    deleteRecursive(dir)

    result
  }

  def createSimpleBlendedContainer(
    mandatoryProperties : List[String] = List.empty,
    sysProperties : Map[String, String] = Map.empty
  ) : Try[BlendedPojoRegistry] = Try {
    System.setProperty("BLENDED_HOME", baseDir)
    System.setProperty("blended.home", baseDir)
    System.setProperty("blended.container.home", baseDir)
    sysProperties.foreach { case (k, v) => System.setProperty(k, v) }
    startBundle(createRegistry().get)(
      classOf[ContainerContext].getPackage().getName(), contextActivator(Some(mandatoryProperties.mkString(",")))
    ).get._2
  }

  def startBundle(sr : BlendedPojoRegistry)(
    symbolicName : String,
    activator : BundleActivator
  ) : Try[(Long, BlendedPojoRegistry)] = Try {

    var bundleId = 0L

    try {
      bundleId = sr.startBundle(symbolicName, activator)
      log.info(s"Started bundle [$symbolicName] with id [$bundleId]")
      (bundleId, sr)
    } catch {
      case NonFatal(e) => throw e
    }
  }

  def stopRegistry(sr : BlendedPojoRegistry) : Unit = {
    // TODO: review: TR thinks, we don't need to sort here
    val bundles = sr.getBundleContext().getBundles().map { b => b.getBundleId() }.sorted.reverse
    bundles.foreach { id =>
      try {
        sr.getBundleContext().getBundle(id).stop()
      } catch {
        case NonFatal(e) => log.error(e)(s"Could not properly stop bundle ID [${id}]")
      }
    }
  }

  def withSimpleBlendedContainer[T](
    mandatoryProperties : List[String] = List.empty
  )(f : BlendedPojoRegistry => T) : Try[T] = Try {

    val registry = createRegistry().get
    val result = f(registry)
    stopRegistry(registry)

    result
  }

  private[this] def deleteRecursive(files : File*) : Unit = files.map { file =>
    if (file.isDirectory) deleteRecursive(file.listFiles.toSeq: _*)
    file.delete match {
      case false if file.exists =>
        throw new RuntimeException(
          s"Could not delete ${if (file.isDirectory) "dir" else "file"}: ${file}"
        )
      case _ =>
    }
  }

  def withStartedBundle[T](sr : BlendedPojoRegistry)(
    symbolicName : String,
    activator : BundleActivator
  )(f : BlendedPojoRegistry => Try[T]) : Try[T] = Try {

    val (bundleId, registry) = startBundle(sr)(symbolicName, activator).get
    val result = f(registry).get
    registry.getBundleContext().getBundle(bundleId).stop()

    result
  }

  def serviceReferences[T](sr : BlendedPojoRegistry)(filter : Option[String] = None)(implicit clazz : ClassTag[T]) : Array[ServiceReference[T]] =

    Option(sr.getServiceReferences(
      clazz.runtimeClass.getName(),
      filter.getOrElse(s"(objectClass=${clazz.runtimeClass.getName()})")
    )).getOrElse(Array.empty).map(_.asInstanceOf[ServiceReference[T]])

  def waitOnService[T](sr : BlendedPojoRegistry, filter : Option[String] = None, timeout : FiniteDuration = 3.seconds)(implicit clazz : ClassTag[T]) : Option[T] = {
    var result : Option[T] = None
    val start = System.currentTimeMillis()

    do {
      result = serviceReferences[T](sr)(filter).headOption.map(ref => sr.getService(ref))
      if (result.isEmpty) {
        Thread.sleep(10)
      }
    } while (System.currentTimeMillis() - start < timeout.toMillis && result.isEmpty)

    result
  }

  def mandatoryService[T](sr : BlendedPojoRegistry, filter : Option[String] = None, timeout : FiniteDuration = 10.seconds)(implicit clazz : ClassTag[T]) : T = {
    val id : String = UUID.randomUUID().toString()
    val start = System.currentTimeMillis()
    log.debug(s"Starting to wait for service of type [${clazz.runtimeClass.getName()}] with filter [$filter] : [$id]")
    waitOnService[T](sr, filter, timeout) match {
      case Some(s) =>
        log.debug(s"Service of type [${clazz.runtimeClass.getName()}] : [$id] is available after [${System.currentTimeMillis() - start} ms]")
        s
      case None    =>
        log.debug(s"Service of type [${clazz.runtimeClass.getName()}] with filter [$filter] : [$id] is unavailable after [$timeout]")
        throw new MandatoryServiceUnavailable(clazz.runtimeClass, filter)
    }
  }

  // Ensure the specified service is gone from the registry
  def ensureServiceMissing[T](sr : BlendedPojoRegistry)(filter : Option[String] = None)(implicit clazz : ClassTag[T], timeout : FiniteDuration) : Try[Unit] = Try {

    var result : Option[T] = None
    val start = System.currentTimeMillis()

    do {
      val ref = serviceReferences[T](sr)(filter).headOption
      result = ref.map(ref => sr.getService[T](ref))
    } while (System.currentTimeMillis() - start < timeout.toMillis && result.isDefined)

    if (result.isDefined) {
      throw new Exception(s"Service of type [${clazz.runtimeClass.getName()}] and filter [$filter] still exists.")
    }
  }

  def withStartedBundles[T](sr : BlendedPojoRegistry)(
    bundles : Seq[(String, BundleActivator)]
  )(f : BlendedPojoRegistry => Try[T]) : Try[T] = {

    bundles match {
      case Seq() => f(sr)
      case head :: tail =>
        withStartedBundle(sr)(
          head._1, head._2
        ) { sr => withStartedBundles(sr)(tail)(f) }
    }
  }

}
