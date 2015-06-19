package blended.updater.config

import com.typesafe.config.Config
import java.io.File
import com.typesafe.config.ConfigRenderOptions
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.io.PrintStream
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory

trait ConfigWriter {

  def write(config: Config, file: File, path: Option[String]): Unit = {
    file.getParentFile() match {
      case null =>
      case parent => parent.mkdirs()
    }
    val ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)))
    val cnf = path.map { p =>
      ConfigFactory.empty().withValue(p, config.root())
    }.getOrElse(config)
    try {
      ps.print(cnf.root().render(
        ConfigRenderOptions.defaults().setOriginComments(false).setComments(false).setFormatted(true).setJson(false)))
    } finally {
      ps.close()
    }
  }

}

object ConfigWriter extends ConfigWriter 