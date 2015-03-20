/*
 * Copyright 2014ff, WoQ - Way of Quality GmbH
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

package de.woq.blended.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;

public class ResourceResolver {

  private ResourceResolver() {}

  public static InputStream openFile(final String location) {
    return openFile(location, Thread.currentThread().getContextClassLoader());
  }

  public static InputStream openFile(final String location, final ClassLoader loader) {

    InputStream is = null;

    try {
      is = new BufferedInputStream(new FileInputStream(location));
    } catch (Exception e) {
    }

    if (is == null) {
      try {
        URL url = new URL(location);
        is = url.openStream();
      } catch (Exception e) {
      }
    }

    if (is == null) {
      try {
        final String path = ResourceResolver.class.getResource(location).getPath();
        is = new FileInputStream(path);
      } catch (Exception e) {
      }
    }

    if (is == null) {
      try {
        is = loader.getResourceAsStream(location);
      } catch (Exception e) {
      }
    }

    if (is == null) {
      is = new ByteArrayInputStream(location.getBytes());
    }

    return is;
  }

}
