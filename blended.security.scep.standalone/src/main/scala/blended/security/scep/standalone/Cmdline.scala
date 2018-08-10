package blended.security.scep.standalone

import de.tototec.cmdoption.CmdOption

class Cmdline {

  @CmdOption(names = Array("--help", "-h"), description = "Show this help", isHelp = true)
  var help: Boolean = false

  @CmdOption(
    names = Array("--password", "-p"),
    args = Array("seed"),
    description = "Generate a password from a given seed and salt. The salt is either implicitly set or explicitly given with --salt"
  )
  var _password: String = _
  def password = Option(_password)

  @CmdOption(
    names = Array("--salt", "-s"),
    args = Array("salt"),
    description = "Use this salt when generating a password with --password"
  )
  var _salt: String = _
  def salt = Option(_salt)

  @CmdOption(
    names = Array("--refresh-certs", "-r"),
    description = "Refresh or initial create a Java key store containing certificates from SCEP server"
  )
  var refreshCerts: Boolean = false

}