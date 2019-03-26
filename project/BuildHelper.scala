import java.nio.file.Files

import sbt.Keys._
import sbt.librarymanagement._
import sbt.librarymanagement.ivy._
import sbt.{Def, File}

object BuildHelper {

  private[this] val log = sbt.util.LogExchange.logger("blended")
  private[this] val ivyConfig = InlineIvyConfiguration().withLog(log)
  private[this] val resolver = IvyDependencyResolution(ivyConfig)

  def deleteRecursive(f : File) : Unit = {
    if (f.isDirectory()) {
      f.listFiles().foreach(deleteRecursive)
    }
    f.delete()
  }

  def resolveModuleFile(mid : ModuleID, targetPath: File) : Vector[File] = {

    resolver.retrieve(mid, None, targetPath, log) match {
      case Left(w) => throw w.resolveException
      case Right(files) => files
    }
  }

  def readVersion(versionFile : File) : Seq[Def.Setting[_]] = {
    val buildVersion = Files.readAllLines(versionFile.toPath()).get(0)

    Seq(
      version := buildVersion,
      isSnapshot := buildVersion.endsWith("SNAPSHOT")
    )
  }
}
