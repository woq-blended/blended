/*
 * Copyright 2014ff,  https://github.com/woq-blended
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package blended.testsupport;

import blended.util.FileHelper;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;

public class XMLMessageFactory implements MessageFactory {

  private final String resourceName;

  private final static Logger LOGGER = LoggerFactory.getLogger(XMLMessageFactory.class);
  private final static DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

  public XMLMessageFactory(String fileName) {
    this.resourceName = fileName;
  }

  @Override
  public Message createTextMessage() throws Exception {
    return createMessage(false);
  }

  @Override
  public Message createBinaryMessage() throws Exception {
    return createMessage(true);
  }

  private Message createMessage(final boolean binary) throws Exception {

    final Message result = new DefaultMessage();
    LOGGER.debug("Creating message from file [{}]", resourceName);

    Document doc = readMessageFile();

    populateHeader(result, doc);
    populateBody(result, doc, binary);

    return result;
  }

  private Document readMessageFile() throws Exception {

    byte[] content = FileHelper.readFile(resourceName);
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

  private void populateBody(final Message message, final Document doc, final boolean binary) throws Exception {

    NodeList textElements = doc.getElementsByTagName("text");
    if (textElements.getLength() > 0) {
      String base64 = ((Element)(textElements.item(0))).getTextContent();
      byte[] decoded = DatatypeConverter.parseBase64Binary(base64);
      if (binary) {
        message.setBody(decoded);
        LOGGER.debug("Set message body to byte Array of length[{}]", decoded.length);
      } else {
        message.setBody(new String(decoded, "UTF-8"));
        LOGGER.debug("Set message body to [{}]", message.getBody(String.class));
      }
    }
  }
}
