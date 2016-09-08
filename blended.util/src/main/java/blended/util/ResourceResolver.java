/*
 * Copyright 2014ff,  https://github.com/woq-blended
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
