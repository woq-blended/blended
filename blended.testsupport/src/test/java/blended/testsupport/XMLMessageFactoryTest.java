package blended.testsupport;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;

public class XMLMessageFactoryTest {

  private final static String FILE = "sampleTestmessage.xml";

  @Test
  public void createMessageTest() throws Exception{

    CamelContext ctxt = new DefaultCamelContext();

    Message msg = new XMLMessageFactory(ctxt, FILE).createTextMessage();
    Assert.assertNotNull(msg);

    Assert.assertTrue(msg.getHeaders().size() > 0);
    Assert.assertNotNull(msg.getBody(String.class));
    Assert.assertEquals("Hallo Andreas", msg.getBody(String.class));
  }
}
