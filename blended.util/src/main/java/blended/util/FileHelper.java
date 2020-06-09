package blended.util;

import java.io.*;

import blended.util.io.StreamCopy;

public class FileHelper {

  public static void writeFile(final File f, byte[] content) throws Exception {

    final InputStream is = new ByteArrayInputStream(content);
    final OutputStream os = new FileOutputStream(f);

    StreamCopy.copy(is, os);

    safeClose(is);
    safeClose(os);
  }

  public static byte[] readStream(InputStream is) throws Exception {

    final ByteArrayOutputStream bos = new ByteArrayOutputStream();

    StreamCopy.copy(is, bos);

    safeClose(is);
    safeClose(bos);

    return bos.toByteArray();
  }

  public static byte[] readFile(final String location) throws Exception {

    final InputStream is = ResourceResolver.openFile(location);
    return readStream(is);
  }

  public static boolean renameFile(File src, File dest, Boolean force) {

    if (force && dest.exists()) {
      dest.delete();
    }

    if (!src.exists() || dest.exists()) {
      return false;
    } else {
      src.renameTo(dest);
      if (!dest.exists() || src.exists()) {
        return false;
      } else {
        return true;
      }
    }
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
