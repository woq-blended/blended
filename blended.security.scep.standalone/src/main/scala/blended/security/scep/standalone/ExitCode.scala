package blended.security.scep.standalone

sealed trait ExitCode {
  def code: Int
}

object ExitCode {
  sealed abstract class ExitCodeBase protected (val code: Int) extends ExitCode
  final case object Ok extends ExitCodeBase(0)
  final case object Error extends ExitCodeBase(1)
  final case object InternalError extends ExitCodeBase(2)
  final case object InvalidCmdline extends ExitCodeBase(3)
  final case object Timeout extends ExitCodeBase(4)
  final case object NoCertsRefreshed extends ExitCodeBase(5)
}
