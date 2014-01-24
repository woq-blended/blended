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

public class MacOSUsage implements Usage {

  @Override
  public void printUsage(final ServiceInstaller installer) {
    System.out.println("");
    System.out.println("At this time it is not known how to get this service to start when the machine is rebooted.");
    System.out.println("If you know how to install the following service script so that it gets started");
    System.out.println("when OS X starts, please email dev@felix.apache.org and let us know how so");
    System.out.println("we can update this message.");
    System.out.println(" ");
    System.out.println("  To start the service:");
    System.out.println("    $ " + installer.getServiceFile().getPath() + " start");
    System.out.println("");
    System.out.println("  To stop the service:");
    System.out.println("    $ " + installer.getServiceFile().getPath() + " stop");
    System.out.println("");

  }
}
