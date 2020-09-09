package blended.itestsupport

object ShellExecutor {

  def excute(cmd : String) : Unit = {
    val process = Runtime.getRuntime().exec(cmd)
    process.waitFor()
  }

}
