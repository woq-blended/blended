package blended.mgmt.ui.backend

object LoginManager {

  var isLoggedIn : Boolean = false

  def loggedIn = isLoggedIn

  def login(user : String, passwd: String) : Unit = {

    isLoggedIn = isLoggedIn || (user == "andreas" || passwd == "secret")
  }

}
