import java.io.FileOutputStream
import java.util.zip.ZipEntry

import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader
import scala.util.matching.Regex
import scala.util.matching.Regex.quoteReplacement

import mill.{Agg, T}
import mill.api.{Ctx, Loose, Result}
import mill.define.{Command, Task}
import mill.scalalib.{Lib, TestModule, TestRunner}
import os.{Path, RelPath}
import sbt.testing.{Fingerprint, Framework}



trait ZipUtil {

  def createZip(outputPath: os.Path,
                inputPaths: Seq[Path],
                explicitEntries: Seq[(RelPath, Path)] = Seq(),
                //                fileFilter: (os.Path, os.RelPath) => Boolean = (p: os.Path, r: os.RelPath) => true,
                prefix: String = "",
                timestamp: Option[Long] = None,
                includeDirs: Boolean = false): Unit = {
    import java.util.zip.ZipOutputStream
    import scala.collection.mutable

    os.remove.all(outputPath)
    val seen = mutable.Set.empty[os.RelPath]
    val zip = new ZipOutputStream(new FileOutputStream(outputPath.toIO))

    try{
      assert(inputPaths.forall(os.exists(_)))
      for{
        p <- inputPaths
        (file, mapping) <-
          if (os.isFile(p)) Iterator(p -> os.rel / p.last)
          else os.walk(p).filter(p => includeDirs || os.isFile(p)).map(sub => sub -> sub.relativeTo(p)).sorted
        if !seen(mapping) // && fileFilter(p, mapping)
      } {
        seen.add(mapping)
        val entry = new ZipEntry(prefix + mapping.toString)
        entry.setTime(timestamp.getOrElse(os.mtime(file)))
        zip.putNextEntry(entry)
        if(os.isFile(file)) zip.write(os.read.bytes(file))
        zip.closeEntry()
      }
    } finally {
      zip.close()
    }
  }

  def unpackZip(src: os.Path, dest: os.Path): Unit = {
    import mill.api.IO

    os.makeDir.all(dest)
    val byteStream = os.read.inputStream(src)
    val zipStream = new java.util.zip.ZipInputStream(byteStream)
    try {
      while ({
        zipStream.getNextEntry match {
          case null => false
          case entry =>
            if (!entry.isDirectory) {
              val entryDest = dest / os.RelPath(entry.getName)
              os.makeDir.all(entryDest / os.up)
              val fileOut = new java.io.FileOutputStream(entryDest.toString)
              try IO.stream(zipStream, fileOut)
              finally fileOut.close()
            }
            zipStream.closeEntry()
            true
        }
      }) ()
    }
    finally zipStream.close()
  }
}
object ZipUtil extends ZipUtil

trait FilterUtil {
  private def applyFilter(
                           source: Path,
                           pattern: Regex,
                           targetDir: Path,
                           relative: os.RelPath,
                           properties: Map[String, String],
                           failOnMiss: Boolean
                         )(implicit ctx: Ctx): (Path, os.RelPath) = {

    def performReplace(in: String): String = {
      val replacer = { m: Regex.Match =>
        val variable = m.group(1)
        val matched = m.matched

        quoteReplacement(properties.getOrElse(
          variable,
          if (failOnMiss) sys.error(s"Unknown variable: [$variable]") else {
            ctx.log.error(s"${source}: Can't replace unknown variable: [${variable}]")
            matched
          }
        ))
      }

      pattern.replaceAllIn(in, replacer)
    }

    val destination = targetDir / relative

    os.makeDir.all(destination / os.up)

    val content = os.read(source)
    os.write(destination, performReplace(content))

    (destination, relative)
  }

  def filterDirs(
                  unfilteredResourcesDirs: Seq[Path],
                  pattern: String,
                  filterTargetDir: Path,
                  props: Map[String, String],
                  failOnMiss: Boolean
                )(implicit ctx: Ctx): Seq[(Path, RelPath)] = {
    val files: Seq[(Path, RelPath)] = unfilteredResourcesDirs.filter(os.exists).flatMap { base =>
      os.walk(base).filter(os.isFile).map(p => p -> p.relativeTo(base))
    }
    val regex = new Regex(pattern)
    val filtered: Seq[(Path, RelPath)] = files.map {
      case (file, relative) => applyFilter(file, regex, filterTargetDir, relative, props, failOnMiss)
    }
    ctx.log.debug("Filtered Resources: " + filtered.mkString(","))
    filtered
  }

}
object FilterUtil extends FilterUtil
