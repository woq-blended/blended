package blended.security

case class LDAPLoginConfig (

  url : String,
  systemUser : String,
  systemPassword: String,
  userBase : String,
  roleBase : String
)

