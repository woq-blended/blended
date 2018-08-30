package blended.security.login.api

import java.util.Date

import blended.security.BlendedPermissions

case class TokenInfo(
  id : String,
  expiration: Date,
  permissions: BlendedPermissions
)
