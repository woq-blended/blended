package blended.streams.file

import java.io.{File, FileOutputStream}

import org.apache.commons.io.FileUtils

trait FileSourceTestSupport {
  def prepareDirectory(dir : String) : File = {

    val f = new File(dir)

    FileUtils.deleteDirectory(f)
    f.mkdirs()

    f
  }

  def genFile(f : File) : Unit = {
    val os = new FileOutputStream(f)
    os.write("Hallo Andreas".getBytes())
    os.flush()
    os.close()
  }
}
