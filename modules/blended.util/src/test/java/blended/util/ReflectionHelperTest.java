package blended.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ReflectionHelperTest {

  @Test
  public void propNames() {

    final Object obj = new PropertiesObject();

    final List<String> propNames = ReflectionHelper.getPropertyNames(obj);

    Assert.assertNotNull(propNames);
    Assert.assertEquals(3, propNames.size());
    Assert.assertTrue(propNames.contains("longProp"));
    Assert.assertTrue(propNames.contains("boolProp"));
    Assert.assertTrue(propNames.contains("intProp"));
  }

  @Test
  public void setLongProp() {
    simpleSetTest(new PropertiesObject(), "longProp", 20l, 20l);
  }

  @Test
  public void setLongPropFromString() {
    simpleSetTest(new PropertiesObject(), "longProp", "20", 20l);
  }

  @Test
  public void setBoolProp() {
    simpleSetTest(new PropertiesObject(), "boolProp", !PropertiesObject.DEFAULT_BOOL, !PropertiesObject.DEFAULT_BOOL);
  }

  @Test
  public void setBoolPropFromString() {
    simpleSetTest(new PropertiesObject(), "boolProp", "false", false);
  }

  @Test
  public void setIntProp() {
    simpleSetTest(new PropertiesObject(), "intProp", 200, 200);
  }

  @Test
  public void setIntFromString() {
    simpleSetTest(new PropertiesObject(), "intProp", "200", 200);
  }

  private void simpleGetTest(final Object obj, final String propName, final Object expected) {
    final Object prop = ReflectionHelper.getProperty(obj, propName);
    Assert.assertEquals(expected, prop);
  }

  private void simpleSetTest(final Object obj, final String propName, final Object value, final Object expected) {
    ReflectionHelper.setProperty(obj, value, propName);
    simpleGetTest(obj, propName, expected);
  }
}
