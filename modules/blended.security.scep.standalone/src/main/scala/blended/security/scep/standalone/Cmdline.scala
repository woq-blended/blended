package blended.security.scep.standalone

import de.tototec.cmdoption.CmdOption

class Cmdline {

  private val defaultTimeout: Int = 20

  @CmdOption(names = Array("--help", "-h"), description = "Show this help", isHelp = true)
  var help: Boolean = false

  @CmdOption(
    names = Array("--password", "-p"),
    args = Array("seed"),
    description = "Generate a password from a given seed and salt. The salt is either implicitly set or explicitly given with --salt"
  )
  var _password: String = _
  def password: Option[String] = Option(_password)

  @CmdOption(
    names = Array("--salt", "-s"),
    args = Array("salt"),
    description = "Use this salt when generating a password with --password"
  )
  var _salt: String = _
  def salt: Option[String] = Option(_salt)

  @CmdOption(
    names = Array("--refresh-certs", "-r"),
    description = "Refresh or initial create a Java key store containing certificates from SCEP server"
  )
  var refreshCerts: Boolean = false

  @CmdOption(
    names = Array("--csr"),
    args = Array("csr-file"),
    description = "Use the given certificate signign request file (CSR) as starting point",
    hidden = true
  )
  var _csrFile: String = _
  def csrFile: Option[String] = Option(_csrFile)

  @CmdOption(
    names = Array("--cert-info"),
    args = Array("cert-file"),
    description = "Try to give some info about the given {0}",
    // hidden, because only implemented partially
    hidden = true
  )
  var _infoFile: String = _
  def infoFile: Option[String] = Option(_infoFile)

  @CmdOption(
    names = Array("--timeout"),
    args = Array("sec"),
    description = "Timeout (in seconds) used when refreshing certificates"
  )
  var timeout: Int = defaultTimeout

  @CmdOption(
    names = Array("--base-dir"),
    args = Array("dir"),
    description = "Alternative base directory (aka 'scepclient.home', used to lookup the 'etc' directory)"
  )
  var _baseDir: String = _
  def baseDir: Option[String] = Option(_baseDir)

  // Must use the hardcoded exit code here, otherwise doc generation fails
  @CmdOption(
    names = Array("--expect-refresh"),
    description = "Expected a refreshed certificate. If no certificate was refreshed the application will exit with exit code 5",
    requires = Array("--refresh-certs")
  )
  var expectRefresh: Boolean = false
}
