package blended.util;

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

    out.flush();
  }
}
