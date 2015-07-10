package blended.updater.config

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions

trait ConfigWriter {

  def write(config: Config, file: File, path: Option[String]): Unit = {
    file.getParentFile() match {
      case null =>
      case parent => parent.mkdirs()
    }
    val ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)))
    try {
      write(config, ps, path)
    } finally {
      ps.close()
    }
  }

  def write(config: Config, os: OutputStream, path: Option[String]): Unit = {
    val ps = new PrintStream(new BufferedOutputStream(os))
    val cnf = path.map { p =>
      ConfigFactory.empty().withValue(p, config.root())
    }.getOrElse(config)
    ps.print(cnf.root().render(
      ConfigRenderOptions.defaults().setOriginComments(false).setComments(false).setFormatted(true).setJson(false)))
    ps.flush()
  }

}

object ConfigWriter extends ConfigWriter 