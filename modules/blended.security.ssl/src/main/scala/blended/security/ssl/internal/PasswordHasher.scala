package blended.security.ssl.internal

import java.security.MessageDigest

class PasswordHasher(salt : String) {

  def password(raw : String) : String = {

    val md = MessageDigest.getInstance("MD5")
    md.reset()

    val digest = md.digest((raw + salt).getBytes()).map { b =>
      Integer.toHexString((b & 0xFF) | 0x100).substring(1)
    }

    digest.mkString("")
  }
}
