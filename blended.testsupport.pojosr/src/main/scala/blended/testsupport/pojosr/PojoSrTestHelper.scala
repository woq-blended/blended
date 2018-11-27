package blended.testsupport.pojosr

import java.io.File

import blended.container.context.api.ContainerIdentifierService
import blended.container.context.impl.internal.ContainerIdentifierServiceImpl
import blended.util.logging.Logger
import domino.DominoActivator
import org.osgi.framework.{BundleActivator, ServiceReference}

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag
import scala.util.Try
import scala.util.control.NonFatal

object PojoSrTestHelper {
  val OnlyOnePojoSrAtATime = new Object()
}

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

  def idSvcActivator(
    mandatoryProperties: Option[String] = None
  ): BundleActivator = {
    new DominoActivator {
      mandatoryProperties.foreach(s =>
        System.setProperty("blended.updater.profile.properties.keys", s))

      whenBundleActive {
        val ctCtxt = new MockContainerContext(baseDir)
        // This needs to be a fixed uuid as some tests might be for restarts and require the same id
        new ContainerIdentifierServiceImpl(ctCtxt) {
          override lazy val uuid: String = pojoUuid
        }.providesService[ContainerIdentifierService]
      }
    }
  }

  def withPojoServiceRegistry[T](f: BlendedPojoRegistry => Try[T]) : Try[T] = Try {

    val registry = createRegistry().get
    val result = f(registry).get
    val dir = new File(System.getProperty(propertyStorage))
    System.clearProperty(propertyStorage)
    deleteRecursive(dir)

    result
  }

  def createSimpleBlendedContainer(
    mandatoryProperties : List[String] = List.empty
  ): Try[BlendedPojoRegistry] = Try {
    System.setProperty("BLENDED_HOME", baseDir)
    System.setProperty("blended.home", baseDir)
    System.setProperty("blended.container.home", baseDir)
    startBundle(createRegistry().get)(
      classOf[ContainerIdentifierServiceImpl].getPackage().getName(), idSvcActivator(Some(mandatoryProperties.mkString(",")))
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

  def stopRegistry(sr: BlendedPojoRegistry): Unit = {
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
    mandatoryProperties: List[String] = List.empty
  )(f: BlendedPojoRegistry => T): Try[T] = Try {

    val registry = createRegistry().get
    val result = f(registry)
    stopRegistry(registry)

    result
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
    activator: BundleActivator
  )(f: BlendedPojoRegistry => Try[T]): Try[T] = Try {

    val (bundleId, registry) = startBundle(sr)(symbolicName, activator).get
    val result = f(registry).get
    registry.getBundleContext().getBundle(bundleId).stop()

    result
  }

  def serviceReferences[T]
    (sr: BlendedPojoRegistry)
    (filter : Option[String] = None)
    (implicit clazz: ClassTag[T]) : Array[ServiceReference[T]] =

      Option(sr.getServiceReferences(
        clazz.runtimeClass.getName(),
        filter.getOrElse(s"(objectClass=${clazz.runtimeClass.getName()})")
      )
  ).getOrElse(Array.empty).map(_.asInstanceOf[ServiceReference[T]])

  def waitOnService[T](sr: BlendedPojoRegistry)(filter : Option[String] = None)(implicit clazz: ClassTag[T], timeout : FiniteDuration) : Option[T] = {
    var result : Option[T] = None
    val start = System.currentTimeMillis()

    do {
      result = serviceReferences[T](sr)(filter).headOption.map(ref => sr.getService(ref))
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
  def ensureServiceMissing[T](sr : BlendedPojoRegistry)(filter : Option[String] = None)(implicit clazz : ClassTag[T], timeout: FiniteDuration) : Try[Unit] = Try {

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

  def withStartedBundles[T](sr: BlendedPojoRegistry)(
      bundles: Seq[(String, BundleActivator)]
  )(f: BlendedPojoRegistry => Try[T]): Try[T] = {

    bundles match {
      case Seq() => f(sr)
      case head :: tail =>
        withStartedBundle(sr)(
          head._1, head._2
        ) { sr => withStartedBundles(sr)(tail)(f) }
    }
  }

}
