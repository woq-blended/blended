package de.woq.osgi.java.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import de.woq.osgi.java.util.ResourceResolver;
import de.woq.osgi.java.util.StreamCopySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileReader {

  private final static Logger LOGGER = LoggerFactory.getLogger(FileReader.class);

  public static byte[] readFile(final String location) throws Exception {

    final InputStream is = ResourceResolver.openFile(location);
    final ByteArrayOutputStream bos = new ByteArrayOutputStream();

    if (is == null) {
      throw new Exception("Unable to read [" + location + "]");
    }

    StreamCopySupport.copyStream(is, bos);

    safeClose(is);
    safeClose(bos);

    return bos.toByteArray();
  }

  private static void safeClose(final InputStream is) {
    if (is != null) {
      try {
        is.close();
      } catch (Exception e) {
        // ignore
      }
    }
  }

  private static void safeClose(final OutputStream os) {
    if (os != null) {
      try {
        os.close();
      } catch (Exception e) {
        // ignore
      }
    }
  }
}
