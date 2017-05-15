package blended.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class StreamCopySupport {

  private StreamCopySupport() {
  }

  public static void copyStream(final InputStream in, final OutputStream out) throws IOException {

    int content = 0;

    do {
      content = in.read();
      if (content >= 0) out.write((byte)content);
    } while (content >= 0);

    out.flush();
  }
}
