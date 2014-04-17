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

import static de.woq.osgi.java.installer.ServiceInstaller.KARAF_ROOT;

public class MacOSFileInstaller implements FileInstaller {

  @Override
  public void installFiles(ServiceInstaller installer) throws Exception {

    final File base = new File(installer.getKarafBase());
    final File bin = new File(base, "bin");
    final File lib = new File(base, "lib");

    ResourceHelper.mkdir(bin);

    File file = new File(bin, installer.getName() + "-wrapper");
    ResourceHelper.copyResourceTo(file, KARAF_ROOT + "macosx/karaf-wrapper");
    ResourceHelper.chmod(file, "a+x");

    ResourceHelper.copyResourceTo(installer.getServiceFile(), "unix/karaf-service", installer.getDefaultWrapperProperties());
    ResourceHelper.chmod(installer.getServiceFile(), "a+x");

    ResourceHelper.copyResourceTo(installer.getWrapperConf(), "unix/karaf-wrapper.conf", installer.getDefaultWrapperProperties());

    ResourceHelper.mkdir(lib);
    ResourceHelper.copyResourceTo(new File(lib, "libwrapper.jnilib"), "macosx/libwrapper.jnilib");
  }
}
