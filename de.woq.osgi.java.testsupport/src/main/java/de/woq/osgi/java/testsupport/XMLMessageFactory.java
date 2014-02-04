package de.woq.osgi.java.testsupport;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;

import de.woq.osgi.java.util.FileReader;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class XMLMessageFactory implements MessageFactory {

  private final String resourceName;

  private final static Logger LOGGER = LoggerFactory.getLogger(XMLMessageFactory.class);
  private final static DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

  public XMLMessageFactory(String fileName) {
    this.resourceName = fileName;
  }

  @Override
  public Message createMessage() throws Exception {

    final Message result = new DefaultMessage();
    LOGGER.debug("Creating message from file [{}]", resourceName);

    Document doc = readMessageFile();

    populateHeader(result, doc);
    populateBody(result, doc);

    return result;
  }

  private Document readMessageFile() throws Exception {

    byte[] content = FileReader.readFile(resourceName);
    InputStream is = new ByteArrayInputStream(content);

    try {
      DocumentBuilder dBuilder = dbf.newDocumentBuilder();
      Document doc = dBuilder.parse(is);
      return doc;
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (Exception e) {
          // ignore
        }
      }
    }
  }

  private void populateHeader(final Message message, final Document doc) throws Exception {

    NodeList headerList = doc.getElementsByTagName("headerProperty");

    for(int i=0; i<headerList.getLength(); i++) {
      Element headerElement = (Element)headerList.item(i);
      String type  = headerElement.getAttribute("type");
      String name  = headerElement.getAttribute("name");
      String value = headerElement.getAttribute("value");

      Class<?> clazz = Class.forName(type);
      Constructor<?> contructor = clazz.getConstructor(String.class);

      Object obj = contructor.newInstance(value);

      LOGGER.debug("Setting property [{}] to [{}]", name, obj.toString());
      message.setHeader(name, obj);
    }
  }

  private void populateBody(final Message message, final Document doc) throws Exception {

    NodeList textElements = doc.getElementsByTagName("text");
    if (textElements.getLength() > 0) {
      String base64 = ((Element)(textElements.item(0))).getTextContent();
      byte[] decoded = DatatypeConverter.parseBase64Binary(base64);
      message.setBody(new String(decoded));
      LOGGER.debug("Set message body to [{}]", message.getBody(String.class));
    }
  }
}
