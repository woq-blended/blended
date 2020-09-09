import mill.contrib.scoverage.api.ScoverageReportWorkerApi.ReportType
import mill.contrib.scoverage.{ScoverageModule, ScoverageReportWorker}
import mill.define.{Command, Module, Task}
import mill.eval.Evaluator
import mill.main.RunScript
import mill.{PathRef, T}
import os.Path

trait ScoverageReport extends Module {
  outer =>
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
    reportTask(evaluator, ReportType.Console, sources, dataTargets)()
  }

  def reportTask(evaluator: Evaluator, reportType: ReportType, sources: String, dataTargets: String): Task[PathRef] = {
    val sourcesTasks: Seq[Task[Seq[PathRef]]] = RunScript.resolveTasks(
      mill.main.ResolveTasks,
      evaluator,
      Seq(sources),
      multiSelect = false
    ) match {
      case Left(err) => throw new Exception(err)
      case Right(tasks) => tasks.asInstanceOf[Seq[Task[Seq[PathRef]]]]
    }
    val dataTasks: Seq[Task[PathRef]] = RunScript.resolveTasks(
      mill.main.ResolveTasks,
      evaluator,
      Seq(dataTargets),
      multiSelect = false
    ) match {
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
}
