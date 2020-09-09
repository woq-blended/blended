package blended.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;

public class ResourceResolver implements URIResolver {

  private static Logger LOG = LoggerFactory.getLogger(ResourceResolver.class);

  public ResourceResolver() {}

  public static InputStream openFile(final String location) {
    return openFile(location, Thread.currentThread().getContextClassLoader());
  }

  public static InputStream openFile(final String location, final ClassLoader loader) {

    InputStream is = null;

    String loc = "" ;

    if (location.length() > 20) {
      loc = location.substring(0, 20) + "...";
    } else {
      loc = location;
    }

    LOG.trace("Resolving resource {}", loc);

    try {
      is = new BufferedInputStream(new FileInputStream(location));
    } catch (Exception e) {
      LOG.trace(e.getMessage());
    }

    if (is == null) {
      try {
        LOG.trace("Resolving resource {} as URL", loc);
        URL url = new URL(location);
        is = url.openStream();
      } catch (Exception e) {
        LOG.trace(e.getMessage());
      }
    }

    if (is == null) {
      try {
        LOG.trace("Resolving resource {} as File", loc);
        final String path = ResourceResolver.class.getResource(location).getPath();
        LOG.trace("Resolved path is {}", path);
        is = new FileInputStream(path);
      } catch (Exception e) {
        LOG.trace(e.getMessage());
      }
    }

    if (is == null) {
      try {
        LOG.trace("Resolving resource {} from Classpath", loc);
        is = loader.getResourceAsStream(location);
      } catch (Exception e) {
        LOG.trace(e.getMessage());
      }
    }

    if (is == null) {
      try {
        is = loader.getResourceAsStream("/" + location);
      } catch (Exception e) {
        LOG.trace(e.getMessage());
      }
    }

    if (is == null) {
      LOG.trace("Resolving resource {} as ByteStream", loc);
      is = new ByteArrayInputStream(location.getBytes());
    }

    return is;
  }

  @Override
  public Source resolve(String href, String base) throws TransformerException {
    return new StreamSource(openFile(href));
  }
}
