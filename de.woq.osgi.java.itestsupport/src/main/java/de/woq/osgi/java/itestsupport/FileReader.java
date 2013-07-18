package de.woq.osgi.java.itestsupport;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileReader {

  private final static Logger LOGGER = LoggerFactory.getLogger(FileReader.class);

  public static byte[] readFile(final String location) throws Exception {

    byte[] buf = new byte[4096];

    InputStream is = openFile(location);
    ByteArrayOutputStream bos = new ByteArrayOutputStream();

    if (is == null) {
      throw new Exception("Unable to read [" + location + "]");
    }

    try {
      for(;;) {
        int count = is.read(buf);

        if (count == -1) {
          break;
        } else {
          bos.write(buf, 0, count);
        }
      }
    } finally {
      is.close();
    }

    return bos.toByteArray();
  }

  private static InputStream openFile(final String compositeLocation) {

    InputStream is = null;

    try {
      LOGGER.info("Trying to read file [" + compositeLocation + "]");
      is = new FileInputStream(compositeLocation);
    } catch (Exception e) {
      LOGGER.info("File [" + compositeLocation + "] not accessible.");
    }

    if (is == null) {
      try {
        LOGGER.info("Trying to read URL [" + compositeLocation + "]");
        URL url = new URL(compositeLocation);
        is = url.openStream();
      } catch (Exception e) {
        LOGGER.info("URL [" + compositeLocation + "] not accessible.");
      }
    }

    if (is == null) {
      try {
        LOGGER.info("Trying to read Resource [" + compositeLocation + "]");
        is = FileReader.class.getResourceAsStream(compositeLocation);
      } catch (Exception e) {
        LOGGER.info("URL [" + compositeLocation + "] not accessible.");
      }
    }

    return is;
  }
}
