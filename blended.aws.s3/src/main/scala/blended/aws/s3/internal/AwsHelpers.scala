package blended.aws.s3.internal

import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec

object AwsHelpers {

  private val algorithm = "HmacSHA256"

  // encrypt a String with a given key
  def hmacSHA256(key: Array[Byte], data: String) : Array[Byte] = {
    val mac = javax.crypto.Mac.getInstance(algorithm)
    mac.init(new SecretKeySpec(key, algorithm))
    mac.doFinal(data.getBytes("UTF-8"))
  }

  def hash(data: Array[Byte]): Array[Byte] = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.reset()
    digest.digest(data)
  }

  def hexEncode(b: Byte) : String = {
    val hex = Integer.toHexString(0xff & b)
    if (hex.length == 1) s"0$hex" else hex
  }

  def hexEncode(bytes: Array[Byte], delim: String = ""): String = {
    bytes.foldLeft(new StringBuffer("")){ case (cur, b) => cur.append(s"${hexEncode(b)}$delim") }.toString
  }

}
