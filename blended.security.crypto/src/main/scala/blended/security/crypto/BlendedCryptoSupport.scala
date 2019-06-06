package blended.security.crypto

import java.io.File
import java.security.Key

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

import scala.io.{BufferedSource, Source}
import scala.util.Try
import scala.util.control.NonFatal

object BlendedCryptoSupport {

  val keyBytes : Int = 16

  def initCryptoSupport(fileName : String) : ContainerCryptoSupport = {

    val defaultPwd : String = "vczP26-QZ5n%$8YP"
    val f : File = new File(fileName)

    var src : Option[BufferedSource] = None

    val pwd : String = try {
      if (f.exists() && f.isFile() && f.canRead()) {
        src = Some(Source.fromFile(f))
        src.map { s =>
          s.getLines().toList.headOption.getOrElse(defaultPwd)
        }.getOrElse(defaultPwd)
      } else {
        defaultPwd
      }
    } catch {
      case NonFatal(t) => defaultPwd
    } finally {
      src.foreach(_.close())
    }

    val secretFromFile : Array[Char] = (pwd + "*" + keyBytes).substring(0, keyBytes).toCharArray()

    val secret : String = {
      val salt : Array[Char] = ("V*YE6FPXW6#!g^hD" + "*" * keyBytes).substring(0, keyBytes).toCharArray()
      val mixed : String = secretFromFile.zip(salt).map { case (a, b) => a.toString + b.toString }.mkString("")
      mixed.substring(0, keyBytes)
    }

    new BlendedCryptoSupport(secret, "AES")
  }
}

class BlendedCryptoSupport(secret : String, alg : String) extends ContainerCryptoSupport {

  import BlendedCryptoSupport.keyBytes

  private val key : Key = {
    val fixedSecret : String = (secret + (" " * keyBytes)).substring(0, keyBytes)
    new SecretKeySpec(fixedSecret.getBytes("UTF-8"), alg)
  }

  private val cipher : Cipher = Cipher.getInstance(alg)

  private val byte2String : Array[Byte] => String = { a =>
    a.map { b =>
      Integer.toHexString((b & 0xFF) | 0x100).substring(1)
    }.mkString("")
  }

  private def string2byte(current : Seq[Byte])(s : String) : Seq[Byte] = {

    val radix : Int = 16

    if (s.equals("")) {
      current
    } else if (s.length() == 1) {
      current :+ Integer.parseInt(s, radix).toByte
    } else {
      string2byte(current :+ Integer.parseInt(s.substring(0, 2), radix).toByte)(s.substring(2))
    }
  }

  def encrypt(plain : String) : Try[String] = Try {
    cipher.init(Cipher.ENCRYPT_MODE, key)
    byte2String(cipher.doFinal(plain.getBytes()))
  }

  def decrypt(encrypted : String) : Try[String] = Try {
    cipher.init(Cipher.DECRYPT_MODE, key)
    val decrypted = cipher.doFinal(string2byte(Seq.empty)(encrypted).toArray)
    new String(decrypted)
  }

}
