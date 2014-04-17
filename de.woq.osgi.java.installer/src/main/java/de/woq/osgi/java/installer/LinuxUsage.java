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

/**
 * Created by andreas on 23/01/14.
 */
public class LinuxUsage implements Usage {

  @Override
  public void printUsage(final ServiceInstaller installer) {

    final File serviceFile = installer.getServiceFile();

    System.out.println("The way the service is installed depends upon your flavor of Linux.");

    System.out.println("");
    System.out.println("On Redhat/Fedora/CentOS Systems:");
    System.out.println("  To install the service:");
    System.out.println("    $ ln -s " + serviceFile.getPath() + " /etc/init.d/");
    System.out.println("    $ chkconfig " + serviceFile.getName() + " --add");
    System.out.println("");
    System.out.println("  To start the service when the machine is rebooted:");
    System.out.println("    $ chkconfig " + serviceFile.getName() + " on");
    System.out.println("");
    System.out.println("  To disable starting the service when the machine is rebooted:");
    System.out.println("    $ chkconfig " + serviceFile.getName() + " off");
    System.out.println("");
    System.out.println("  To start the service:");
    System.out.println("    $ service " + serviceFile.getName() + " start");
    System.out.println("");
    System.out.println("  To stop the service:");
    System.out.println("    $ service " + serviceFile.getName() + " stop");
    System.out.println("");
    System.out.println("  To uninstall the service :");
    System.out.println("    $ chkconfig " + serviceFile.getName() + " --del");
    System.out.println("    $ rm /etc/init.d/" + serviceFile.getName());

    System.out.println("");
    System.out.println("On Ubuntu/Debian Systems:");
    System.out.println("  To install the service:");
    System.out.println("    $ ln -s " + serviceFile.getPath() + " /etc/init.d/");
    System.out.println("");
    System.out.println("  To start the service when the machine is rebooted:");
    System.out.println("    $ update-rc.d " + serviceFile.getName() + " defaults");
    System.out.println("");
    System.out.println("  To disable starting the service when the machine is rebooted:");
    System.out.println("    $ update-rc.d -f " + serviceFile.getName() + " remove");
    System.out.println("");
    System.out.println("  To start the service:");
    System.out.println("    $ /etc/init.d/" + serviceFile.getName() + " start");
    System.out.println("");
    System.out.println("  To stop the service:");
    System.out.println("    $ /etc/init.d/" + serviceFile.getName() + " stop");
    System.out.println("");
    System.out.println("  To uninstall the service :");
    System.out.println("    $ rm /etc/init.d/" + serviceFile.getName());

  }
}
