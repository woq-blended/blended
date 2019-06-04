package blended.security

import javax.security.auth.callback._

class PasswordCallbackHandler(name : String, password : Array[Char]) extends CallbackHandler {

  override def handle(callbacks : Array[Callback]) : Unit = {
    callbacks.foreach { cb : Callback =>
      cb match {
        case nameCallback : NameCallback    => nameCallback.setName(name)
        case pwdCallback : PasswordCallback => pwdCallback.setPassword(password)
        case other                          => throw new UnsupportedCallbackException(other, "The submitted callback is not supported")
      }
    }
  }
}
