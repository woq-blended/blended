package blended.util;

import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;

public class XMLSupportTest {

  @Test
  public void parseTest() throws Exception {

    final XMLSupport xmlSupport = new XMLSupport("SOAPResponse.xml", XMLSupport.class.getClassLoader());
    final Document document = xmlSupport.getDocument();

    Assert.assertNotNull(document);
  }

  @Test
  public void applyXPathTest() throws Exception {
    final XMLSupport xmlSupport = new XMLSupport("SOAPResponse.xml", XMLSupport.class.getClassLoader());
    final Document document = xmlSupport.getDocument();

    Assert.assertNotNull(document);

    final String rc = xmlSupport.applyXPath("/SOAP-ENV:Envelope/SOAP-ENV:Body/returncode/@id");
    Assert.assertEquals("0", rc);

  }
}
