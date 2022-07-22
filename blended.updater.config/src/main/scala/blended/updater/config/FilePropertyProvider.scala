package blended.updater.config

import java.io.{File, FileReader}
import java.util.Properties

import scala.util.Try

class FilePropertyProvider(file : File) extends PropertyProvider {

  private[this] val props : Properties = {
    val props = new Properties()
    Try { props.load(new FileReader(file)) }
    //      .recover {
    //        case NonFatal(e) => log.error("Could not read properties file: {}", Array(file, e))
    //      }
    props
  }

  override def provide(key : String) : Option[String] = Option(props.getProperty(key))
}
