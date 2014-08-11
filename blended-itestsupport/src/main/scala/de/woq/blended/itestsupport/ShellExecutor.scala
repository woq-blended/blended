package de.woq.blended.itestsupport

object ShellExecutor {

  def excute(cmd : String) {
    val process = Runtime.getRuntime().exec(cmd)
    process.waitFor()
  }

}
