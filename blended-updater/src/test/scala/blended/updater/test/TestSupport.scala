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
    f(file)
    if (!file.delete()) {
      file.deleteOnExit()
    }
  }

  def withTestDir(deleteAfter: Boolean = true)(f: File => Any): Unit = {
    val file = File.createTempFile("test", "")
    file.delete()
    file.mkdir()
    f(file)
    if (deleteAfter) deleteRecursive(file)
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