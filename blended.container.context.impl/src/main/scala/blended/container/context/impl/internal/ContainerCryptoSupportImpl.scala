package blended.container.context.impl.internal

import java.io.File
import java.security.Key

import blended.container.context.api.ContainerCryptoSupport
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

import scala.io.Source
import scala.util.Try

object ContainerCryptoSupportImpl {

  def initCryptoSupport(fileName : String) : ContainerCryptoSupport = {

    val secretFromFile : Array[Char] =
      (Source.fromFile(
        new File(fileName)
      ).getLines().toList.headOption.getOrElse("vczP26-QZ5n%$8YP") + ("*" * 16))
        .substring(0,16)
        .toCharArray()

    val secret : String = {
      val salt : Array[Char] = ("V*YE6FPXW6#!g^hD" + "*" * 16).substring(0,16).toCharArray()
      val mixed : String = secretFromFile.zip(salt).map { case (a,b) => a.toString + b.toString} mkString("")
      mixed.substring(0,16)
    }

    new ContainerCryptoSupportImpl(secret, "AES")
  }
}

class ContainerCryptoSupportImpl(secret : String, alg : String) extends ContainerCryptoSupport {

  private val KEYBYTES = 16

  private val key : Key = {
    val fixedSecret : String = (secret + (" " * KEYBYTES)).substring(0, KEYBYTES)
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
      string2byte(current :+ Integer.parseInt(s.substring(0,2), radix).toByte)(s.substring(2))
    }
  }

  def encrypt(plain: String) : Try[String] = Try {
    cipher.init(Cipher.ENCRYPT_MODE, key)
    byte2String(cipher.doFinal(plain.getBytes()))
  }

  def decrypt(encrypted : String) : Try[String] = Try {
    cipher.init(Cipher.DECRYPT_MODE, key)
    val decrypted = cipher.doFinal(string2byte(Seq.empty)(encrypted).toArray)
    new String(decrypted)
  }

}
