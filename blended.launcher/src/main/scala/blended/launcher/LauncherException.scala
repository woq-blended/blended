package blended.launcher

class LauncherException(msg : String, cause : Option[Throwable] = None, val errorCode : Int)
  extends RuntimeException(msg, cause.orNull)
