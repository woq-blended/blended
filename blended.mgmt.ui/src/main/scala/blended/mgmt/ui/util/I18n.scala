package blended.mgmt.ui.util

import java.util.Locale
import scala.reflect.ClassTag
import scala.reflect.classTag
import java.util.MissingResourceException
import java.util.ResourceBundle
import java.text.MessageFormat
import java.util.jar.JarInputStream
import java.io.File
import java.io.FileInputStream
import scala.collection.mutable.WeakHashMap
import scala.util.Try
import java.util.regex.Matcher

trait I18nMarker {

  private[this] val log = Logger[I18nMarker]

  @inline def marktr(msgid: String): String = msgid
  @inline def marktrc(context: String, msgid: String): String = msgid
  @inline def notr(msgid: String, params: Any*): String = params match {
    case Seq() => msgid
    case _ =>
      var idx = 0
      var msg = msgid
      val args = params.map(_.toString).foreach { arg =>
        msg = msg.replaceAll("\\{" + idx + "\\}", Matcher.quoteReplacement(arg))
        idx += 1
      }
      msg
  }
}

trait I18n extends I18nMarker {
  def tr(msgid: String, params: Any*): String
  def trn(msgid: String, msgidPlural: String, n: Long, params: Any*): String
  def trc(context: String, msgid: String, params: Any*): String
  def trcn(context: String, msgid: String, msgidPlural: String, n: Long, params: Any*): String
  def locale: String
  def preparetr(msgid: String, params: Any*): PreparedI18n
}

trait PreparedI18n {
  def tr: String
  def notr: String
}

object I18n extends I18nMarker {

  private[this] val log = Logger[I18n.type]

  var missingTranslationDecorator: Option[String => String] = None

  def apply(): I18n = apply("")
  def apply(locale: String): I18n = {
    new I18nImpl(locale, missingTranslationDecorator)
  }
}

class I18nImpl(override val locale: String, missingTranslationDecorator: Option[String => String]) extends I18n {

  private[this] lazy val log = Logger[I18nImpl]

  override def toString = "I18nImpl(locale=" + locale + ")"

  private[this] def translate(msgid: String): String = {
    // TODO: make a message lookup
    log.trace("Could not lookup msgid \"" + msgid + "\" for locale \"" + locale + "\"")
    missingTranslationDecorator.orElse(I18n.missingTranslationDecorator) match {
      case Some(miss) => miss(msgid)
      case None => msgid
    }
  }

  override def tr(msgid: String, params: Any*): String = notr(translate(msgid), params: _*)

  override def trn(msgid: String, msgidPlural: String, n: Long, params: Any*): String = tr(n match {
    case 1 => msgid
    case _ => msgidPlural
  }, params: _*)

  override def trc(context: String, msgid: String, params: Any*): String = tr(msgid, params: _*)

  override def trcn(context: String, msgid: String, msgidPlural: String, n: Long, params: Any*): String = trn(msgid, msgidPlural, n, params: _*)

  @inline override def preparetr(msgid: String, params: Any*): PreparedI18n = new PreparedI18n {
    override def tr: String = I18nImpl.this.tr(msgid, params: _*)
    override def notr: String = I18nImpl.this.notr(msgid, params: _*)
  }

}