package blended.jmx

import blended.util.logging.Logger

import javax.management.ObjectName

import scala.util.{Failure, Success, Try}

/**
 * MBean registry (server facade) that supports exporting and removal of scala products and therefore scala case classes.
 */
trait OpenMBeanExporter {
  import OpenMBeanExporter._

  /**
   * Exports a scala product / case class.
   * @param product The product
   * @param replaceExisting If `true`, an already registered MBean with the same ObjectName will removed before the registration.
   * @param namingStrategy Used to automatically decide which ObjectName should be used to register the product.
   * @return In case of error, a [[Failure]] of [[javax.management.InstanceAlreadyExistsException]]
   */
  def export(product: Product, replaceExisting: Boolean = false)(implicit namingStrategy: NamingStrategy): Try[Unit] = {
    export(product, namingStrategy(product), replaceExisting)
  }

  /**
   * Exports a scala product / case class under the given ObjectName.
   * @param product The product
   * @param objectName The ObjectName that should be used to register the product.
   * @param replaceExisting If `true`, an already registered MBean with the same ObjectName will removed before the registration.
   */
  def export(product: Product, objectName: ObjectName, replaceExisting: Boolean): Try[Unit]

  def exportSafe(product: Product, objectName: ObjectName, replaceExisting : Boolean)(log : Logger) : Unit =
    export(product, objectName, replaceExisting) match {
      case Success(_) =>
      case Failure(e) =>
        log.warn(e, stackTrace = true)(s"Jmx update for [${objectName.toString()}] with [$product], replaceExisting [$replaceExisting] failed : [${e.getMessage()}]")
    }

  def objectName(product: Product)(implicit namingStrategy: NamingStrategy): ObjectName = namingStrategy(product)

  /**
   * Removes a previously registered product or MBean with the given ÒbjectName.
   */
  def remove(objectName: ObjectName): Try[Unit]

  def removeSafe(objectName : ObjectName)(log : Logger) =
    remove(objectName) match {
      case Success(_) =>
      case Failure(e) => log.warn(s"Remove og JMX MBean [${objectName.toString()}] failed : [${e.getMessage()}]")
    }

}

object OpenMBeanExporter {

  /**
   * Partial function that extracts a [[ObjectName]] from an product.
   */
  type NamingStrategy = PartialFunction[Any, ObjectName]

  /**
   * NamingStrategy that uses the full classname as ObjectName.
   */
  val UseFullClassName: NamingStrategy = {
    case mbean =>
      val c = mbean.getClass()
      val domain = c.getPackage().getName()
      val name = c.getSimpleName()
      new ObjectName(s"${domain}:type=${name}")
  }

  /**
   * Default naming strategy.
   */
  implicit val namingStrategy: NamingStrategy = UseFullClassName
}

