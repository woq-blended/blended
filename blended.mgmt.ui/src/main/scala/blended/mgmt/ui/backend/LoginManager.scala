package blended.mgmt.ui.backend

class LoginFailedException extends Exception

object LoginManager {

  private[this] var isLoggedIn : Boolean = false

  def loggedIn = isLoggedIn

  def login(user : String, passwd: String) : Unit = {

    if (!isLoggedIn) {
      if ( user == "andreas" && passwd == "secret")
        isLoggedIn = true
      else
        throw new LoginFailedException
    }
  }

  def logout() : Unit = {
    isLoggedIn = false
  }

}
