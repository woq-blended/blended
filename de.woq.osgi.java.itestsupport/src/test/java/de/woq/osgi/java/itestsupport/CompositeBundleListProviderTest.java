package de.woq.osgi.java.itestsupport;

import org.junit.Assert;
import org.junit.Test;

public class CompositeBundleListProviderTest {

  @Test
  public void readEntriesTest() {

    try {
      new CompositeBundleListProvider("classpath:specs.composite").getBundles();
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

}
