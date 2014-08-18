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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

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
