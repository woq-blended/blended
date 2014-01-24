/*
 * Copyright 2013, WoQ - Way of Quality UG(mbH)
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

public class WindowsFileInstaller implements FileInstaller {

  @Override
  public void installFiles(final ServiceInstaller installer) throws Exception {

    final File base = new File(installer.getKarafBase());
    final File bin = new File(base, "bin");
    final File lib = new File(base, "lib");

    final String arch = System.getProperty("os.arch");

    if (arch.equalsIgnoreCase("amd64") || arch.equalsIgnoreCase("x86_64")) {
      ResourceHelper.mkdir(bin);

      ResourceHelper.copyResourceTo(new File(bin, installer.getName() + "-wrapper.exe"), "windows64/karaf-wrapper.exe", false);

      ResourceHelper.copyFilteredResourceTo(installer.getServiceFile(), "windows64/karaf-wrapper.conf", installer.getDefaultWrapperProperties());
      ResourceHelper.copyFilteredResourceTo(installer.getServiceFile(), "windows64/karaf-service.bat", installer.getDefaultWrapperProperties());

      ResourceHelper.mkdir(lib);
      ResourceHelper.copyResourceTo(new File(lib, "wrapper.dll"), "windows64/wrapper.dll", false);
    } else {
      ResourceHelper.mkdir(bin);

      ResourceHelper.copyResourceTo(new File(bin, installer.getName() + "-wrapper.exe"), "windows/karaf-wrapper.exe", false);
      ResourceHelper.copyFilteredResourceTo(installer.getWrapperConf(), "windows/karaf-wrapper.conf", installer.getDefaultWrapperProperties());
      ResourceHelper.copyFilteredResourceTo(installer.getServiceFile(), "windows/karaf-service.bat", installer.getDefaultWrapperProperties());

      ResourceHelper.mkdir(lib);
      ResourceHelper.copyResourceTo(new File(lib, "wrapper.dll"), "windows/wrapper.dll", false);
    }
  }
}
