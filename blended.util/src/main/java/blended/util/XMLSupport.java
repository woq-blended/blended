package blended.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.InputStream;
import java.util.Iterator;

public class XMLSupport {

  private final String location;
  private final ClassLoader loader;

  private Document document = null;

  private static final XPathFactory XPATH_FACTORY = XPathFactory.newInstance();
  private static final DocumentBuilderFactory DOC_BUILDER_FACTORY;

  static {
    DOC_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();
    DOC_BUILDER_FACTORY.setXIncludeAware(true);
    DOC_BUILDER_FACTORY.setNamespaceAware(true);
  }

  public XMLSupport(final String location) {
    this(location, XMLSupport.class.getClassLoader());
  }

  public XMLSupport(final String location, final ClassLoader loader) {
    this.loader = loader;
    this.location = location;
  }

  public Document getDocument() throws Exception {

    if (document == null) {
      final DocumentBuilder builder = DOC_BUILDER_FACTORY.newDocumentBuilder();
      final InputStream is = ResourceResolver.openFile(location, loader);

      if (is == null) {
        throw new Exception("Location [" + location + "] couldn't be resolved.");
      }

      document = builder.parse(new InputSource(is));

      try {
        is.close();
      } catch (Exception ignore) {}
    }

    return document;
  }

  public String applyXPath(final String query) throws Exception {

    final Element element = getDocument().getDocumentElement();

    try {

      final XPath xpath = XPATH_FACTORY.newXPath();

      if (element.getNamespaceURI() != null) {
        xpath.setNamespaceContext(new NamespaceContext() {
          @Override
          public String getNamespaceURI(String prefix) {
            return element.getNamespaceURI();
          }

          @Override
          public String getPrefix(String namespaceURI) {
            throw new UnsupportedOperationException();
          }

          @Override
          public Iterator getPrefixes(String namespaceURI) {
            throw new UnsupportedOperationException();
          }
        });
      }
      final XPathExpression expr = xpath.compile(query);
      return expr.evaluate(element);


    } catch (Exception e) {
      final String msg = "Error evaluating XPath [" + query + "] on element [" + element.getTagName() + "].";
      throw new Exception(msg, e);
    }
  }

  public void validate(final String schemaLocation) throws Exception {

    final Source schemaFile = new StreamSource(ResourceResolver.openFile(schemaLocation));
    final Source content = new StreamSource(ResourceResolver.openFile(location));

    final SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    final Schema schema = schemaFactory.newSchema(schemaFile);
    final Validator validator = schema.newValidator();
    validator.validate(content);
  }

}


