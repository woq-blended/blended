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

package blended.karaf.installer;

import java.io.File;

public class SolarisFileInstaller implements FileInstaller {

  @Override
  public void installFiles(ServiceInstaller installer) throws Exception {

    final File base = new File(installer.getKarafBase());
    final File bin = new File(base, "bin");
    final File lib = new File(base, "lib");

    final File file = new File(bin, installer.getName() + "-wrapper");
    final String arch = System.getProperty("os.arch");

    ResourceHelper.mkdir(bin);
    ResourceHelper.mkdir(lib);

    ResourceHelper.copyResourceTo(installer.getServiceFile(), "unix/karaf-service", installer.getDefaultWrapperProperties());
    ResourceHelper.chmod(installer.getServiceFile(), "a+x");

    ResourceHelper.copyResourceTo(installer.getWrapperConf(), "unix/karaf-wrapper.conf", installer.getDefaultWrapperProperties());

    if (arch.equalsIgnoreCase("sparc")) {
      ResourceHelper.copyResourceTo(file, "solaris/sparc64/karaf-wrapper");
      ResourceHelper.copyResourceTo(new File(lib, "libwrapper.so"), "solaris/sparc64/libwrapper.so");
    } else if (arch.equalsIgnoreCase("x86")) {
      ResourceHelper.copyResourceTo(file, "solaris/x86/karaf-wrapper");
      ResourceHelper.copyResourceTo(new File(lib, "libwrapper.so"), "solaris/x86/libwrapper.so");
    } else {
      ResourceHelper.copyResourceTo(file, "solaris/sparc32/karaf-wrapper");
      ResourceHelper.copyResourceTo(new File(lib, "libwrapper.so"), "solaris/sparc32/libwrapper.so");
    }

    ResourceHelper.chmod(file, "a+x");
  }
}
