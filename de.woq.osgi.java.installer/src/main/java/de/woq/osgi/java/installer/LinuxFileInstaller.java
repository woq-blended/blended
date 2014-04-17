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

package de.woq.osgi.java.installer;

import java.io.File;

import static de.woq.osgi.java.installer.ServiceInstaller.WOQ_ROOT;

public class LinuxFileInstaller implements FileInstaller {

  @Override
  public void installFiles(ServiceInstaller installer) throws Exception {

    final File base = new File(installer.getKarafBase());
    final File bin = new File(base, "bin");
    final File lib = new File(base, "lib");

    final String arch = System.getProperty("os.arch");
    final File file = new File(bin, installer.getName() + "-wrapper");

    ResourceHelper.mkdir(bin);
    ResourceHelper.mkdir(lib);

    ResourceHelper.copyResourceTo(installer.getServiceFile(), "unix/karaf-service", installer.getDefaultWrapperProperties());
    ResourceHelper.chmod(installer.getServiceFile(), "a+x");

    ResourceHelper.copyResourceTo(installer.getWrapperConf(), WOQ_ROOT + "/unix/karaf-wrapper.conf", installer.getDefaultWrapperProperties());

    if (arch.equalsIgnoreCase("amd64") || arch.equalsIgnoreCase("x86_64")) {
      ResourceHelper.copyResourceTo(file, "linux64/karaf-wrapper");
      ResourceHelper.copyResourceTo(new File(lib, "libwrapper.so"), "linux64/libwrapper.so");
    } else {
      ResourceHelper.copyResourceTo(file, "linux/karaf-wrapper");
      ResourceHelper.copyResourceTo(new File(lib, "libwrapper.so"), "linux/libwrapper.so");
    }

    ResourceHelper.chmod(file, "a+x");
  }
}
