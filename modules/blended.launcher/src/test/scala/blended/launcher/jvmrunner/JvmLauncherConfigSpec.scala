package blended.launcher.jvmrunner

import java.io.File

import blended.testsupport.scalatest.LoggingFreeSpec
import scala.concurrent.duration._

import org.scalatest.matchers.should.Matchers

class JvmLauncherConfigSpec extends LoggingFreeSpec with Matchers {

  "JvmLauncherConfig" - {
    "should parse classpath" in {
      val config = JvmLauncherConfig.parse(Seq("-cp=file.jar;file2.jar"))
      assert(config.classpath === Seq(new File("file.jar"), new File("file2.jar")))
    }
    "should parse action (start|stop)" in {
      val configStart = JvmLauncherConfig.parse(Seq("start"))
      assert(configStart.action === Some("start"))
      val configStop = JvmLauncherConfig.parse(Seq("stop"))
      assert(configStop.action === Some("stop"))
    }
    "should parse other args" in {
      val config = JvmLauncherConfig.parse(Seq("--", "arg1", "arg2"))
      assert(config.otherArgs === Seq("arg1", "arg2"))
    }
    "should parse jvm options" in {
      val config = JvmLauncherConfig.parse(Seq("-jvmOpt=opt1", "-jvmOpt=opt2=4"))
      assert(config.jvmOpts === Seq("opt1", "opt2=4"))
    }
    "should parse shutdown timeout" in {
      val config = JvmLauncherConfig.parse(Seq("-maxShutdown=10"))
      assert(config.shutdownTimeout === 10.seconds)
    }
    "should parse interactive mode" in {
      val config = JvmLauncherConfig.parse(Seq("-interactive=true"))
      assert(config.interactive === true)
      val config2 = JvmLauncherConfig.parse(Seq("-interactive=false"))
      assert(config2.interactive === false)
    }
    "should parse restart deplay" in {
      val config = JvmLauncherConfig.parse(Seq("-restartDelay=2"))
      assert(config.restartDelaySec === Some(2))
    }
    "should fail with invalid arguments" in {
      intercept[RuntimeException] {
        JvmLauncherConfig.parse(Seq("invalid", "args"))
      }
    }
    "should not validate default config" in {
      val checked = JvmLauncherConfig.checkConfig(JvmLauncherConfig())
      assert(checked.isFailure === true)
    }
    "should validate minimal config" in {
      val checked = JvmLauncherConfig.checkConfig(JvmLauncherConfig(action = Some("start")))
      assert(checked.isSuccess === true)
    }
  }

}
