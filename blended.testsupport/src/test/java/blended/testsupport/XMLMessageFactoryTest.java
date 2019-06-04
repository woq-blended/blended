package blended.testsupport;

import org.apache.camel.Message;
import org.junit.Assert;
import org.junit.Test;

public class XMLMessageFactoryTest {

  private final static String FILE = "sampleTestmessage.xml";

  @Test
  public void createMessageTest() throws Exception{

    Message msg = new XMLMessageFactory(FILE).createTextMessage();
    Assert.assertNotNull(msg);

    Assert.assertTrue(msg.getHeaders().size() > 0);
    Assert.assertNotNull(msg.getBody(String.class));
    Assert.assertEquals("Hallo Andreas", msg.getBody(String.class));
  }
}
