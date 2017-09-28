package blended.mgmt.ui.components

import blended.mgmt.ui.routes.NavigationInfo
import blended.mgmt.ui.util.{FormHelper, I18n, Logger}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.VdomElement
import japgolly.scalajs.react.vdom.html_<^._

object LoginComponent {

  def apply[T](target: T)(n: NavigationInfo[T]) = new LoginComponent[T](target).component(n)
}

class LoginComponent[T](target : T) {

  val log : Logger = Logger[LoginComponent.type]
  val i18n = I18n()

  case class LoginState(
    user: String = "",
    passwd: String = ""
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

    val onSubmit : ReactEvent => Callback = { e =>
      Callback.info("Logging in ...")
    }


    def render(n: NavigationInfo[T], s: LoginState) : VdomElement = <.div(
      ^.width := "400px",
      ^.margin := "auto",
      ContentPanel(Some("Login"))(<.form(
        ^.onSubmit ==> onSubmit,
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
          value = s.user,
          lblWidth = "120px",
          changeCallback = onPasswdChange
        ),
        <.div(
          ^.display := "flex",
          ^.justifyContent.flexEnd,
          ^.flexDirection.row,
          <.input(
            ^.display := "flex",
            ^.`type` := "submit",
            ^.cls := "btn btn-primary btn-large",
            ^.value := i18n.tr("Login")
          )
        )
      ))
    )
  }

  val component = ScalaComponent.builder[NavigationInfo[T]]("LoginComponent")
    .initialState(LoginState())
    .renderBackend[Backend]
    .build

}
