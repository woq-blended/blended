package blended.launcher

class LauncherException(msg: String, cause: Throwable = null, val errorCode: Int) extends RuntimeException(msg, cause)