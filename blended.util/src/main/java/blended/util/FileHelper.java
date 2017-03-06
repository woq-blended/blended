package blended.util;

import java.io.*;

public class FileHelper {

  public static void writeFile(final File f, byte[] content) throws Exception {

    final InputStream is = new ByteArrayInputStream(content);
    final OutputStream os = new FileOutputStream(f);

    StreamCopySupport.copyStream(is, os);

    safeClose(is);
    safeClose(os);
  }

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
