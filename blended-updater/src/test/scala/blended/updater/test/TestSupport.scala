package blended.updater.test

import java.io.PrintStream
import java.io.FileOutputStream
import java.io.File
import java.util.UUID
import java.io.BufferedOutputStream

trait TestSupport {

  def nextId(): String = UUID.randomUUID().toString()

  def withTestFile[T](content: String)(f: File => T): T = {
    val file = File.createTempFile("test", "")
    if (!file.exists()) {
      throw new AssertionError("Just created file does not exist: " + file)
    }

    val fos = new FileOutputStream(file)
    val os = new PrintStream(new BufferedOutputStream(fos))
    try {
      try {
        os.print(content)
      } finally {
        os.flush()
        // fos.getFD().sync()
        os.close()
      }
      f(file)
    } finally {
      if (!file.delete()) {
        file.deleteOnExit()
      }
    }
  }

  def withTestFiles[T](content1: String, content2: String)(f: (File, File) => T): T =
    withTestFile(content1) { file1 =>
      withTestFile(content2) { file2 =>
        f(file1, file2)
      }
    }

  def withTestFiles[T](content1: String, content2: String, content3: String)(f: (File, File, File) => T): T =
    withTestFiles(content1, content2) { (file1, file2) =>
      withTestFile(content3) { file3 =>
        f(file1, file2, file3)
      }
    }

  def withTestFiles[T](content1: String, content2: String, content3: String, content4: String)(f: (File, File, File, File) => T): T =
    withTestFiles(content1, content2, content3) { (file1, file2, file3) =>
      withTestFile(content4) { file4 =>
        f(file1, file2, file3, file4)
      }
    }

  def withTestFiles(content1: String, content2: String, content3: String, content4: String, content5: String)(f: (File, File, File, File, File) => Any): Unit =
    withTestFiles(content1, content2, content3, content4) { (file1, file2, file3, file4) =>
      withTestFile(content4) { file5 =>
        f(file1, file2, file3, file4, file5)
      }
    }

  def withTestDir[T](deleteAfter: Boolean = true)(f: File => T): T = {
    val file = File.createTempFile("test", "")
    file.delete()
    file.mkdir()
    try {
      f(file)
    } finally {
      if (deleteAfter) deleteRecursive(file)
    }
  }

  def deleteRecursive(file: File): Unit = {
    if (file.isDirectory()) {
      file.listFiles() match {
        case null =>
        case files => files.foreach(deleteRecursive)
      }
    }
    file.delete()
  }

}

object TestSupport extends TestSupport