package blended.security.scep.standalone

class ExitAppException(val exitCode : ExitCode, val errMsg : Option[String] = None, cause : Throwable = null)
  extends RuntimeException(
    errMsg match {
      case None      => s"${exitCode}"
      case Some(msg) => s"${msg} (exit code ${exitCode})"
    },
    cause
  )
