package blended.mgmt.ui.components

import blended.mgmt.ui.backend.LoginManager
import blended.mgmt.ui.routes.{MgmtRouter, NavigationInfo}
import blended.mgmt.ui.util.{FormHelper, I18n, Logger}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.VdomElement
import japgolly.scalajs.react.vdom.html_<^._

import scala.util.control.NonFatal

object LoginComponent {

  def apply[T](target: T)(n: NavigationInfo[T]) = new LoginComponent[T](target).component(n)
}

class LoginComponent[T](target : T) {

  val log : Logger = Logger[LoginComponent.type]
  val i18n = I18n()

  case class LoginState(
    user: String = "",
    passwd: String = "",
    failed : Boolean = false
  )

  class Backend(scope: BackendScope[NavigationInfo[T], LoginState]) {

    val onUserChange : ReactEventFromTextArea => Callback = { e =>
      e.extract(_.target.value) { v =>
        scope.modState(_.copy(user = v))
      }
    }

    val onPasswdChange : ReactEventFromTextArea => Callback = { e =>
      e.extract(_.target.value) { v =>
        scope.modState(_.copy(passwd = v))
      }
    }

    def onLogin(n: NavigationInfo[T], s : LoginState) : ReactEvent => Callback = { e =>

      try {
        LoginManager.login(s.user, s.passwd)
        n.ctl.set(target)
      } catch {
        case NonFatal(_) =>
          scope.modState(s => s.copy(failed = true))
      }
    }

    def render(n: NavigationInfo[T], s: LoginState) : VdomElement = <.div(
      ^.width := "400px",
      ^.margin := "auto",
      ContentPanel(Some("Login"))(<.form(
        FormHelper.input(
          id = "name",
          label = "Name",
          inputType = "text",
          value = s.user,
          lblWidth = "120px",
          changeCallback = onUserChange
        ),
        FormHelper.input(
          id = "passwd",
          label = "Password",
          inputType = "password",
          value = s.passwd,
          lblWidth = "120px",
          changeCallback = onPasswdChange
        ),
        <.div(
          ^.padding := "10px",
          ^.borderRadius := "3px",
          ^.background := "#d9534f",
          ^.textAlign.center,
          ^.marginBottom := "1em",
          i18n.tr("Login failed")
        ).when(s.failed),
        <.div(
          ^.display := "flex",
          ^.justifyContent.flexEnd,
          ^.flexDirection.row,
          <.button(
            ^.display := "flex",
            ^.`type` := "submit",
            ^.cls := "btn btn-primary btn-large",
            ^.onClick ==> onLogin(n,s),
            i18n.tr("Login")
          )
        )
      ))
    )
  }

  val component = ScalaComponent.builder[NavigationInfo[T]]("LoginComponent")
    .initialState(LoginState())
    .renderBackend[Backend]
    .componentDidMount( c => Callback {
      log.trace(s"Login target is [$target]")
      if (LoginManager.loggedIn) {
        c.props.ctl.set(target).runNow()
      }
    }
    )
    .build

}
