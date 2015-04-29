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

package de.wayofquality.blended.karaf.installer;

import org.apache.karaf.shell.wrapper.PumpStreamHandler;

import java.io.*;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public final class ResourceHelper {

  private ResourceHelper() {
  }

  public static void createJar(final File outFile, final String resource) throws Exception {

    if (!outFile.exists()) {
      System.out.println("Creating file: " + outFile.getPath());

      final InputStream is = ResourceHelper.class.getClassLoader().getResourceAsStream(resource);

      if (is == null) {
        throw new IllegalStateException("Resource " + resource + " not found!");
      }

      try {
        final JarOutputStream jar = new JarOutputStream(new FileOutputStream(outFile));
        int idx = resource.indexOf('/');
        while (idx > 0) {
          jar.putNextEntry(new ZipEntry(resource.substring(0, idx)));
          jar.closeEntry();
          idx = resource.indexOf('/', idx + 1);
        }
        jar.putNextEntry(new ZipEntry(resource));
        int c;
        while ((c = is.read()) >= 0) {
          jar.write(c);
        }
        jar.closeEntry();
        jar.close();
      } finally {
        safeClose(is);
      }
    }
  }

  public static int chmod(final File serviceFile, final String mode) throws Exception {
    ProcessBuilder builder = new ProcessBuilder();
    builder.command("chmod", mode, serviceFile.getCanonicalPath());
    Process p = builder.start();

    PumpStreamHandler handler = new PumpStreamHandler(System.in, System.out, System.err);
    handler.attach(p);
    handler.start();
    int status = p.waitFor();
    handler.stop();
    return status;
  }

  public static void copyResourceTo(final File outFile, final String resource) throws Exception {
    copyResourceTo(outFile, resource, false, null);
  }

  public static void copyResourceTo(final File outFile, final String resource, final Map<String, String> props) throws Exception {
    copyResourceTo(outFile, resource, true, props);
  }

  public static void copyResourceTo(final File outFile, final String resource, final boolean text, final Map<String, String> props) throws Exception {
    if (!outFile.exists()) {

      System.out.println("Creating file: " + outFile.getPath());

      final String location = (resource.startsWith("/") ? resource : ServiceInstaller.KARAF_ROOT + resource);
      final InputStream is = ResourceHelper.class.getResourceAsStream(location);
      final BufferedReader reader = new BufferedReader(new InputStreamReader(is));

      try {
        if (text) {
          // Read it line at a time so that we can use the platform line ending when we write it out.
          PrintStream out = new PrintStream(new FileOutputStream(outFile));
          try {
            String line = "";
            while (line != null) {
              line = reader.readLine();
              if (line != null) {
                line = filter(line, props);
                out.println(line);
              }
            }
          } finally {
            safeClose(out);
          }
        } else {
          // Binary so just write it out the way it came in.
          FileOutputStream out = new FileOutputStream(outFile);
          try {
            int c = 0;
            while ((c = is.read()) >= 0) {
              out.write(c);
            }
          } finally {
            safeClose(out);
          }
        }
      } finally {
        safeClose(is);
      }
    } else {
      System.out.println("File already exists. Move it out of the way if you wish to recreate it: " + outFile.getPath());
    }
  }

  private static String filter(final String line, final Map<String, String> props) {

    String result = line;

    if (props != null) {
      for (Map.Entry<String, String> i : props.entrySet()) {
        int p1 = line.indexOf(i.getKey());
        if (p1 >= 0) {
          String l1 = line.substring(0, p1);
          String l2 = line.substring(p1 + i.getKey().length());
          result = l1 + i.getValue() + l2;
        }
      }
    }
    return result;
  }

  public static void mkdir(final File file) {
    if (!file.exists()) {
      System.out.println("Creating missing directory: " + file.getPath());
      file.mkdirs();
    }
  }

  private static void safeClose(final InputStream is) throws IOException {
    if (is == null) {
      return;
    }

    try {
      is.close();
    } catch (Throwable ignore) {
    }
  }

  private static void safeClose(final OutputStream os) throws IOException {
    if (os == null) {
      return;
    }

    try {
      os.close();
    } catch (Throwable ignore) {
    }
  }
}
