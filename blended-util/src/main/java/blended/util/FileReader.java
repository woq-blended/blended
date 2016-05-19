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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class FileReader {

  public static byte[] readStream(InputStream is) throws Exception {

    final ByteArrayOutputStream bos = new ByteArrayOutputStream();

    StreamCopySupport.copyStream(is, bos);

    safeClose(is);
    safeClose(bos);

    return bos.toByteArray();
  }

  public static byte[] readFile(final String location) throws Exception {

    final InputStream is = ResourceResolver.openFile(location);
    return readStream(is);
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
