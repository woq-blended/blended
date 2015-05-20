package blended.updater.test

import java.io.PrintStream
import java.io.FileOutputStream
import java.io.File
import java.util.UUID

trait TestSupport {

  def nextId(): String = UUID.randomUUID().toString()

  def withTestFile(content: String)(f: File => Any): Unit = {
    val file = File.createTempFile("test", "")
    val os = new PrintStream(new FileOutputStream(file))
    os.print(content)
    os.close()
    try {
      f(file)
    } finally {
      if (!file.delete()) {
        file.deleteOnExit()
      }
    }
  }

  def withTestFiles(content1: String, content2: String)(f: (File, File) => Any): Unit =
    withTestFile(content1) { file1 =>
      withTestFile(content2) { file2 =>
        f(file1, file2)
      }
    }

  def withTestFiles(content1: String, content2: String, content3: String)(f: (File, File, File) => Any): Unit =
    withTestFiles(content1, content2) { (file1, file2) =>
      withTestFile(content3) { file3 =>
        f(file1, file2, file3)
      }
    }

  def withTestFiles(content1: String, content2: String, content3: String, content4: String)(f: (File, File, File, File) => Any): Unit =
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

  def withTestDir(deleteAfter: Boolean = true)(f: File => Any): Unit = {
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