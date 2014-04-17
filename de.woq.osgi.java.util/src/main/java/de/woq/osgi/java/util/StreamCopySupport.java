/*
 * Copyright 2014ff, WoQ - Way of Quality UG(mbH)
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class StreamCopySupport {

  private StreamCopySupport() {
  }

  private static final int BUF_SIZE = 32768;

  public static void copyStream(final InputStream in, final OutputStream out) throws IOException {

    final byte[] buf = new byte[BUF_SIZE];

    int count = 0;

    do {
      count = in.read(buf, 0, buf.length);
      if (count > 0) {
        out.write(buf, 0, count);
      }
    } while (count >= 0);
  }
}
