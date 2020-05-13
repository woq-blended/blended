import java.io.FileOutputStream
import java.util.zip.ZipEntry

import scala.util.matching.Regex
import scala.util.matching.Regex.quoteReplacement

import mill.{PathRef, T}
import mill.api.Ctx
import mill.contrib.scoverage.{ScoverageModule, ScoverageReportWorker}
import mill.contrib.scoverage.api.ScoverageReportWorkerApi.ReportType
import mill.define.{Command, Module, Task}
import mill.eval.Evaluator
import mill.main.RunScript
import os.{Path, RelPath}

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

//
//object Scoverage extends ExternalModule {
//  def worker: Worker[ScoverageReportWorker] = T.worker { ScoverageReportWorker.scoverageReportWorker() }
//  object workerModule extends ScoverageModule {
//    override def scalaVersion = Deps.scalaVersion
//    override def scoverageVersion = Deps.scoverageVersion
//  }
//  def htmlReportAll(sources: mill.main.Tasks[Seq[PathRef]], dataTargets: mill.main.Tasks[PathRef]): Command[Unit] = T.command {
//    val sourcePaths: Seq[Path] = T.sequence(sources.value)().flatten.map(_.path)
//    val dataPaths: Seq[Path] = T.sequence(dataTargets.value)().map(_.path)
//    worker()
//      .bridge(workerModule.toolsClasspath().map(_.path))
//      .report(ReportType.Html, sourcePaths, dataPaths)
//  }
//
//  // parse tasks
//  implicit def millScoptTargetReads[T]: scopt.Read[Tasks[T]] = new mill.main.Tasks.Scopt[T]()
////   find modules
//  lazy val millDiscover: mill.define.Discover[this.type] = mill.define.Discover[this.type]
//}

trait ScoverageReport extends Module { outer =>
  def scalaVersion: T[String]
  def scoverageVersion: T[String]

  def scoverageReportWorkerModule: ScoverageReportWorker.type = ScoverageReportWorker
  /** We use this only to get access to the right classpaths */
  object workerModule extends ScoverageModule {
    override def scalaVersion = outer.scalaVersion
    override def scoverageVersion = outer.scoverageVersion
  }

  def htmlReportAll(evaluator: Evaluator, sources: String = "__.allSources", dataTargets: String = "__.scoverage.data"): Command[PathRef] = T.command {
    reportTask(evaluator, ReportType.Html, sources, dataTargets)()
  }

  def xmlReportAll(evaluator: Evaluator, sources: String = "__.allSources", dataTargets: String = "__.scoverage.data"): Command[PathRef] = T.command {
    reportTask(evaluator, ReportType.Xml, sources, dataTargets)()
  }

  def consoleReportAll(evaluator: Evaluator, sources: String = "__.allSources", dataTargets: String = "__.scoverage.data"): Command[PathRef] = T.command {
    reportTask(evaluator,ReportType.Console, sources, dataTargets)()
  }

  def reportTask(evaluator: Evaluator, reportType: ReportType, sources: String, dataTargets: String): Task[PathRef] = {
    val sourcesTasks: Seq[Task[Seq[PathRef]]] = RunScript.resolveTasks(
      mill.main.ResolveTasks,
      evaluator,
      Seq(sources),
      multiSelect = false
    ) match{
      case Left(err) => throw new Exception(err)
      case Right(tasks) => tasks.asInstanceOf[Seq[Task[Seq[PathRef]]]]
    }
    val dataTasks: Seq[Task[PathRef]] = RunScript.resolveTasks(
      mill.main.ResolveTasks,
      evaluator,
      Seq(dataTargets),
      multiSelect = false
    ) match{
      case Left(err) => throw new Exception(err)
      case Right(tasks) => tasks.asInstanceOf[Seq[Task[PathRef]]]
    }

    T.task {
      val sourcePaths: Seq[Path] = T.sequence(sourcesTasks)().flatten.map(_.path)
      val dataPaths: Seq[Path] = T.sequence(dataTasks)().map(_.path)
      scoverageReportWorkerModule.scoverageReportWorker()
        .bridge(workerModule.toolsClasspath().map(_.path))
        .report(reportType, sourcePaths, dataPaths)
      PathRef(T.dest)
    }
  }

  // parse tasks
  //  implicit def millScoptTargetReads[T]: scopt.Read[Tasks[T]] = new mill.main.Tasks.Scopt[T]()
  //   find modules
  //  lazy val millDiscover: mill.define.Discover[this.type] = mill.define.Discover[this.type]
}