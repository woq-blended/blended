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

package de.woq.blended.karaf.installer;

public class WindowsUsage implements Usage {

  @Override
  public void printUsage(final ServiceInstaller installer) {
    System.out.println("");
    System.out.println("To install the service, run: ");
    System.out.println("  C:> " + installer.getServiceFile().getPath() + " install");
    System.out.println("");
    System.out.println("Once installed, to start the service run: ");
    System.out.println("  C:> net start \"" + installer.getName() + "\"");
    System.out.println("");
    System.out.println("Once running, to stop the service run: ");
    System.out.println("  C:> net stop \"" + installer.getName() + "\"");
    System.out.println("");
    System.out.println("Once stopped, to remove the installed the service run: ");
    System.out.println("  C:> " + installer.getServiceFile().getPath() + " remove");
    System.out.println("");
  }
}
