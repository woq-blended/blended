package blended.streams.testsupport

import java.io.ByteArrayInputStream

import akka.util.ByteString
import blended.streams.message.FlowMessage.FlowMessageProps
import blended.streams.message.{FlowMessage, MsgProperty}
import blended.util.FileHelper
import blended.util.logging.Logger
import javax.xml.bind.DatatypeConverter
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.{Document, Element}

import scala.util.Try

class StreamsMessageFactory(fileName : String) {

  private val log = Logger[StreamsMessageFactory]
  private val dbf = DocumentBuilderFactory.newInstance

  def createMessage(binary: Boolean) : Try[FlowMessage] = Try {

    log.debug(s"Creating message from file [$fileName]")
    val doc = readMessageFile().get
    val header = readHeader(doc).get

    readBody(doc, binary).get match {
      case None => FlowMessage(header)
      case Some(Left(bs)) => FlowMessage(bs)(header)
      case Some(Right(t)) => FlowMessage(t)(header)
    }
  }

  private def readMessageFile() : Try[Document] = Try {

    val content = FileHelper.readFile(fileName)
    val is = new ByteArrayInputStream(content)

    try {
      val dBuilder = dbf.newDocumentBuilder
      val doc = dBuilder.parse(is)
      doc
    } finally {
      if (is != null) {
        is.close()
      }
    }
  }

  private def readHeader(doc: Document): Try[FlowMessageProps] = Try {

    val headerList = doc.getElementsByTagName("headerProperty")
    val headers : Seq[Element] = 0.to(headerList.getLength - 1).map(i => headerList.item(i).asInstanceOf[Element])

    headers.map { elem =>
      val propType = elem.getAttribute("type")
      val name = elem.getAttribute("name")
      val value = elem.getAttribute("value")
      val clazz = Class.forName(propType)
      val constructor = clazz.getConstructor(classOf[String])
      val obj = constructor.newInstance(value)

      name -> MsgProperty.lift(obj).get
    }.toMap
  }

  private def readBody(doc: Document, binary: Boolean): Try[Option[Either[ByteString, String]]] = Try {

    val textElements = doc.getElementsByTagName("text")

    if (textElements.getLength > 0) {
      val base64 = textElements.item(0).asInstanceOf[Element].getTextContent
      val decoded = DatatypeConverter.parseBase64Binary(base64)
      if (binary) {
        log.debug(s"Read message body as byte Array of length[${decoded.length}]")
        Some(Left(ByteString(decoded)))
      }
      else {
        val text = new String(decoded, "UTF-8")
        log.debug(s"Read message body as String of length[${text.length}]")
        Some(Right(text))
      }
    } else {
      None
    }
  }
}
