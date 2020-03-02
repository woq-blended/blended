package blended.streams.file

import java.io.File

import blended.testsupport.BlendedTestSupport

class DirectorySourceSpec extends AbstractFileSourceSpec {

  "The Directory Source should" - {

    "not deliver the same file within the poll interval" in {

      val pollCfg : FilePollConfig = FilePollConfig(rawCfg, ctCtxt).copy(
        sourceDir = BlendedTestSupport.projectTestOutput + "/dirSource",
        pattern = Some("^.*txt$")
      )

      prepareDirectory(pollCfg.sourceDir)
      genFile(new File(pollCfg.sourceDir, "test.txt"))

      val dirSource : DirectorySource = new DirectorySource(pollCfg)

      dirSource.nextFile() should be (defined)
      dirSource.nextFile() should be (empty)

      Thread.sleep(pollCfg.interval.toMillis + 20)
      dirSource.nextFile() should be (defined)
    }
  }
}
