package de.woq.osgi.java.itestsupport;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompositeFileReader {

  private final static Character COMMENT_TOKEN = '#';
  private final static Logger LOGGER = LoggerFactory.getLogger(CompositeFileReader.class);

  public static String[] compositeEntries(final String compositeLocation) throws Exception {

    List<String> result = new ArrayList<String>();

    byte[] content = FileReader.readFile(compositeLocation);
    InputStream is = new ByteArrayInputStream(content);

    BufferedReader reader = new BufferedReader(new InputStreamReader(is));

    try {
      for(;;) {
        String line = reader.readLine();
        if (line == null) {
          break;
        } else {
          line = line.trim();
          if (line.indexOf(COMMENT_TOKEN) != -1) {
            line = line.substring(0, line.indexOf(COMMENT_TOKEN)).trim();
          }
          if (line.length() > 0) {
            result.add(line);
          }
        }
      }
    } finally {
      is.close();
    }

    return result.toArray(new String[] {});
  }
}
