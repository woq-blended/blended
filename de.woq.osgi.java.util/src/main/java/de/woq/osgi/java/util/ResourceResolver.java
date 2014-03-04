/*
 * Copyright 2013, WoQ - Way of Quality UG(mbH)
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

package de.woq.osgi.java.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;

public class ResourceResolver {

  private final static Logger LOGGER = LoggerFactory.getLogger(ResourceResolver.class);

  private ResourceResolver() {}

  public static InputStream openFile(final String location) {
    return openFile(location, Thread.currentThread().getContextClassLoader());
  }

  public static InputStream openFile(final String location, final ClassLoader loader) {

    InputStream is = null;

    try {
      LOGGER.debug("Trying to read file [" + location + "]");
      is = new BufferedInputStream(new FileInputStream(location));
    } catch (Exception e) {
      LOGGER.debug("File [" + location + "] not accessible.");
    }

    if (is == null) {
      try {
        LOGGER.debug("Trying to read URL [" + location + "]");
        URL url = new URL(location);
        is = url.openStream();
      } catch (Exception e) {
        LOGGER.debug("URL [" + location + "] not accessible.");
      }
    }

    if (is == null) {
      try {
        LOGGER.debug("Trying to read Resource [" + location + "]");
        final String path = ResourceResolver.class.getResource(location).getPath();
        is = new FileInputStream(path);
      } catch (Exception e) {
        LOGGER.debug("URL [" + location + "] not accessible.");
      }
    }

    if (is == null) {
      try {
        LOGGER.debug("Trying to read Resource [" + location + "]");
        is = loader.getResourceAsStream(location);
      } catch (Exception e) {
        LOGGER.debug("URL [" + location + "] not accessible.");
      }
    }

    if (is == null) {
      final String logString = location.length() < 50 ? location : (location.substring(0, 50) + "...");
      LOGGER.debug("Using [" + logString + "] as input stream");
      is = new ByteArrayInputStream(location.getBytes());
    }

    return is;
  }

}
