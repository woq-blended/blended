package blended.updater.config

import java.io.File
import java.util.Properties
import java.io.FileReader
import scala.util.Try
import scala.util.control.NonFatal

class FilePropertyProvider(file: File) extends PropertyProvider {

  private[this] val props: Properties = {
    val props = new Properties()
    Try { props.load(new FileReader(file)) }
    //      .recover {
    //        case NonFatal(e) => log.error("Could not read properties file: {}", Array(file, e))
    //      }
    props
  }

  override def provide(key: String): Option[String] = Option(props.getProperty(key))
}