package blended.testsupport

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.io.SyncFailedException
import java.util.UUID

trait TestFile {
  import TestFile._

  def nextId(): String = UUID.randomUUID().toString()

  def deleteAfter[T](files: File*)(f: => T)(implicit delete: DeletePolicy): T = {
    var failure = false
    try {
      f
    } catch {
      case e: Throwable =>
        failure = true
        throw e
    } finally {
      delete match {
        case DeleteAlways => deleteRecursive(files: _*)
        case DeleteWhenNoFailure if !failure => deleteRecursive(files: _*)
        case _ =>
      }
    }
  }

  def withTestFile[T](content: String, dir: File)(f: File => T)(implicit delete: DeletePolicy): T = {
    val file = File.createTempFile("test", "", dir)
    if (!file.exists()) {
      throw new AssertionError("Just created file does not exist: " + file)
    }

    deleteAfter(file) {
      val fos = new FileOutputStream(file)
      val os = new PrintStream(new BufferedOutputStream(fos))
      try {
        os.print(content)
      } finally {
        os.flush()
        fos.flush()
        try {
          fos.getFD().sync()
        } catch {
          case e: SyncFailedException => // at least we tried
        }
        os.close()
      }
      f(file)
    }
  }

  def withTestFile[T](content: String)(f: File => T)(implicit delete: DeletePolicy): T = {

    val file = File.createTempFile("test", "")
    if (!file.exists()) {
      throw new AssertionError("Just created file does not exist: " + file)
    }

    deleteAfter(file) {
      writeFile(file, content)
      f(file)
    }
  }

  def writeFile(file: File, content: String): Unit = writeToFile(file, append = false, content)
 
  def appendFile(file: File, content: String): Unit = writeToFile(file, append = true, content)
  
  
  private[this] def writeToFile(file: File, append: Boolean, content: String): Unit = {
    Option(file.getParentFile()) foreach { parent =>
      parent.mkdirs()
    }
    val fos = new FileOutputStream(file, append)
    val os = new PrintStream(new BufferedOutputStream(fos))
    try {
      os.print(content)
    } finally {
      os.flush()
      fos.flush()
      try {
        fos.getFD().sync()
      } catch {
        case e: SyncFailedException => // at least we tried
      }
      os.close()
    }
  }

  def withTestFiles[T](content1: String, content2: String)(f: (File, File) => T)(implicit delete: DeletePolicy): T =
    withTestFile(content1) { file1 =>
      withTestFile(content2) { file2 =>
        f(file1, file2)
      }
    }

  def withTestFiles[T](content1: String, content2: String, content3: String)(f: (File, File, File) => T)(implicit delete: DeletePolicy): T =
    withTestFiles(content1, content2) { (file1, file2) =>
      withTestFile(content3) { file3 =>
        f(file1, file2, file3)
      }
    }

  def withTestFiles[T](content1: String, content2: String, content3: String, content4: String)(f: (File, File, File, File) => T)(implicit delete: DeletePolicy): T =
    withTestFiles(content1, content2, content3) { (file1, file2, file3) =>
      withTestFile(content4) { file4 =>
        f(file1, file2, file3, file4)
      }
    }

  def withTestFiles(content1: String, content2: String, content3: String, content4: String, content5: String)(f: (File, File, File, File, File) => Any)(implicit delete: DeletePolicy): Unit =
    withTestFiles(content1, content2, content3, content4) { (file1, file2, file3, file4) =>
      withTestFile(content4) { file5 =>
        f(file1, file2, file3, file4, file5)
      }
    }

  def withTestDir[T](tmpDir: File)(f: File => T)(implicit delete: DeletePolicy): T = {
    if (tmpDir != null && !tmpDir.exists()) tmpDir.mkdirs()
    val file = File.createTempFile("test", "", tmpDir)
    file.delete()
    file.mkdir()
    deleteAfter(file) {
      f(file)
    }
  }

  def withTestDir[T]()(f: File => T)(implicit delete: DeletePolicy): T = withTestDir(null)(f)(delete)

  def deleteRecursive(files: File*): Unit = files.foreach { file =>
    if (file.isDirectory()) {
      file.listFiles() match {
        case null =>
        case files => deleteRecursive(files: _*)
      }
    }
    file.delete()
  }

  def listFilesRecursive(file: File): Seq[String] = {
    def list(file: File, prefix: String): List[String] = {
      file.listFiles() match {
        case null => Nil
        case files =>
          files.toList.flatMap { f =>
            val newPrefix = if (prefix.isEmpty()) f.getName() else s"${prefix}/${f.getName()}"
            newPrefix :: list(f, newPrefix)
          }
      }
    }
    list(file, "").sorted
  }

}

object TestFile extends TestFile {

  sealed trait DeletePolicy
  case object DeleteAlways extends DeletePolicy
  case object DeleteNever extends DeletePolicy
  case object DeleteWhenNoFailure extends DeletePolicy

}