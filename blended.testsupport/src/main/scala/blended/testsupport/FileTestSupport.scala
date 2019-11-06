package blended.testsupport

import java.io._
import java.util.zip._

import akka.util.ByteString
import blended.util.logging.Logger
import org.apache.commons.io.FileUtils
import org.scalatest.Matchers

import scala.io.Source
import scala.util.control.NonFatal

trait FileTestSupport extends  Matchers {

  private val log : Logger = Logger[FileTestSupport]

  val duplicateFilter : FileFilter = new FileFilter {
    override def accept(f: File): Boolean = f.isDirectory() || (f.isFile() && f.getName().contains("dup_"))
  }

  val acceptAllFilter : FileFilter = new FileFilter {
    override def accept(pathname: File): Boolean = true
  }

  def multiply(c : ByteString, n: Int) : ByteString = n match {
    case m if m > 1 => c ++ multiply(c, n-1)
    case m if m == 1 => c
    case _ => ByteString("")
  }

  def zipCompress(content: ByteString) : ByteString = {

    val bos = new ByteArrayOutputStream()
    val zip = new ZipOutputStream(bos)

    val entry = new ZipEntry("entry")
    zip.putNextEntry(entry)

    zip.write(content.toArray)

    zip.flush()
    bos.flush()

    zip.close()
    bos.close()

    ByteString(bos.toByteArray)
  }

  def gzipCompress(content: ByteString) : ByteString = {

    val bos = new ByteArrayOutputStream()
    val zip = new GZIPOutputStream(bos)

    zip.write(content.toArray)

    zip.flush()
    bos.flush()

    zip.close()
    bos.close()

    ByteString(bos.toByteArray())
  }

  def uncompress(content: Array[Byte]): Array[Byte] = {

    val bis = new BufferedInputStream(new ByteArrayInputStream(content))
    val bos = new ByteArrayOutputStream()

    val zippedIs : InputStream = try {
      log.debug("Trying to use GZIP compression")
      new GZIPInputStream(new BufferedInputStream(new ByteArrayInputStream(content)))
    } catch {
      case NonFatal(e) =>
        log.debug("Trying to use ZIP compression")
        val zis = new ZipInputStream(new BufferedInputStream(new ByteArrayInputStream(content)))
        zis.getNextEntry()
        zis
    }

    val buffer : Array[Byte] = new Array[Byte](2048)

    var count = zippedIs.read(buffer)

    while(count >= 0) {
      if (count > 0) {
        bos.write(buffer, 0, count)
      }
      count = zippedIs.read(buffer)
    }

    bis.close()
    bos.flush()
    bos.close()

    bos.toByteArray
  }

  def getFiles(dirName : String, pattern : String, recursive : Boolean) : List[File] =
    getFiles(
      dirName = dirName,
      recursive = recursive,
      filter = (f : File) => f.getName().matches(pattern)
    )

  def getFiles(dirName: String, filter: FileFilter, recursive : Boolean): List[File] = {

    val f : File = new File(dirName)

    if (f.exists) {
      if (f.isFile()) {
        List(f.getAbsoluteFile())
      } else {
        f.listFiles(filter).flatMap {
          x => if (x.isFile) {
            List(x.getAbsoluteFile())
          } else {
            if (recursive) {
              getFiles(x.getAbsolutePath, filter, recursive)
            } else {
              List.empty
            }
          }
        }.toList
      }
    } else {
      List.empty
    }
  }

  def verifyTargetFile (file: File, content : ByteString) : Boolean = {

    val fileContent : ByteString = ByteString(Source.fromFile(file).mkString)

    if (content.length == fileContent.length) {
      content.zip(fileContent).map { case (b1, b2) => b1 == b2 }.forall(x => x)
    } else {
      false
    }
  }

  def cleanUpDirectory (dirName : String) : Unit = {

    log.info(s"Cleaning up directory [$dirName]")
    FileUtils.deleteDirectory(new File(dirName))
    FileUtils.forceMkdir(new File(dirName))
  }

  def genFile(f: File, content : ByteString) : Unit = {
    val os = new FileOutputStream(f)
    os.write(content.toArray)
    os.flush()
    os.close()
  }
}
