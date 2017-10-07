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

    LOG.debug("Resolving resource {}", location);

    try {
      is = new BufferedInputStream(new FileInputStream(location));
    } catch (Exception e) {
      LOG.debug(e.getMessage());
    }

    if (is == null) {
      try {
        LOG.debug("Resolving resource {} as URL", location);
        URL url = new URL(location);
        is = url.openStream();
      } catch (Exception e) {
        LOG.debug(e.getMessage());
      }
    }

    if (is == null) {
      try {
        LOG.debug("Resolving resource {} as File", location);
        final String path = ResourceResolver.class.getResource(location).getPath();
        LOG.debug("Resolved path is {}", path);
        is = new FileInputStream(path);
      } catch (Exception e) {
        LOG.debug(e.getMessage());
      }
    }

    if (is == null) {
      try {
        LOG.debug("Resolving resource {} from Classpath", location);
        is = loader.getResourceAsStream(location);
      } catch (Exception e) {
        LOG.debug(e.getMessage());
      }
    }

    if (is == null) {
      try {
        is = loader.getResourceAsStream("/" + location);
      } catch (Exception e) {
        LOG.debug(e.getMessage());
      }
    }

    if (is == null) {
      LOG.debug("Resolving resource {} as ByteStream", location);
      is = new ByteArrayInputStream(location.getBytes());
    }

    return is;
  }

  @Override
  public Source resolve(String href, String base) throws TransformerException {
    return new StreamSource(openFile(href));
  }
}
