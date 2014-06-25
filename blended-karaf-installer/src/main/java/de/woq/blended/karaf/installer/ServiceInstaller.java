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
package de.woq.blended.karaf.installer;

import de.woq.blended.util.ReflectionHelper;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class ServiceInstaller {

  public final static String KARAF_ROOT = "/org/apache/karaf/shell/wrapper/";
  public final static String WOQ_ROOT = "/" + ServiceInstaller.class.getPackage().getName().replaceAll("\\.", "/");

  private String name = "karaf";
  private String displayName;
  private String description = "";
  private String startType = "AUTO_START";

  private String karafBase = null;
  private String karafHome = null;
  private String karafData = null;

  private File serviceFile = null;
  private File wrapperConf = null;

  public static void main(final String[] args) {

    try {
      final ServiceInstaller installer = new ServiceInstaller(args);

      if (installer.getKarafBase() == null) {
        System.out.println("The Karaf Base directory must be set using the -b parameter.");
        System.exit(-1);
      }

      installer.doExecute();
      System.out.println("Service Installation successfull.");
      System.exit(0);
    } catch (Exception e) {
      System.out.println("Service install failed with Exception:");
      e.printStackTrace();
      System.exit(-1);
    }
  }

  private ServiceInstaller(final String[] args) throws Exception {
    parseCommandLine(args);
  }

  private void parseCommandLine(final String[] args) {
    final Map<String, String> params = new HashMap<String, String>();

    params.put("-n", "name");
    params.put("-dn", "displayName");
    params.put("_D", "description");
    params.put("-s", "startType");
    params.put("-b", "karafBase");
    params.put("-h", "karafHome");
    params.put("-d", "karafData");

    for(int i=0; i<args.length; i++) {

      final String key = args[i];

      if (!params.containsKey(args[i])) {
        System.out.println("Ignoring unrecognized command line argument " + args[i]);
      } else {
        i++;
        if (args.length > i) {
          ReflectionHelper.setProperty(this, params.get(key), args[i]);
        }
      }
    }
  }

  protected void doExecute() throws Exception {

    try {
      String name = getName();
      final File base = new File(getKarafBase());
      final File bin = new File(base, "bin");
      final File etc = new File(base, "etc");
      final File lib = new File(base, "lib");

      final String os = System.getProperty("os.name", "Unknown");

      if (os.startsWith("Win")) {
        setServiceFile(new File(bin, name + "-service.bat"));
        setWrapperConf(new File(etc, name + "-wrapper.conf"));
        new WindowsFileInstaller().installFiles(this);
      } else if (os.startsWith("Mac OS X")) {
        setServiceFile(new File(bin, name + "-service"));
        setWrapperConf(new File(etc, name + "-wrapper.conf"));
        new MacOSFileInstaller().installFiles(this);
      } else if (os.startsWith("Linux")) {
        setServiceFile(new File(bin, name + "-service"));
        setWrapperConf(new File(etc, name + "-wrapper.conf"));
        new LinuxFileInstaller().installFiles(this);
      } else if (os.startsWith("AIX")) {
        setServiceFile(new File(bin, name + "-service"));
        setWrapperConf(new File(etc, name + "-wrapper.conf"));
        new AIXFileInstaller().installFiles(this);
      } else if (os.startsWith("Solaris") || os.startsWith("SunOS")) {
        setServiceFile(new File(bin, name + "-service"));
        setWrapperConf(new File(etc, name + "-wrapper.conf"));
        new SolarisFileInstaller().installFiles(this);
      } else if (os.startsWith("HP-UX") || os.startsWith("HPUX")) {
        setServiceFile(new File(bin, name + "-service"));
        setWrapperConf(new File(etc, name + "-wrapper.conf"));
        new HPUXFileInstaller().installFiles(this);
      } else {
        throw new Exception("Your operating system '" + os + "' is not currently supported.");
      }

      // Install the wrapper jar to the lib directory..
      ResourceHelper.mkdir(lib);
      ResourceHelper.copyResourceTo(new File(lib, "karaf-wrapper.jar"), "all/karaf-wrapper.jar");
      ResourceHelper.mkdir(etc);

      ResourceHelper.createJar(new File(lib, "karaf-wrapper-main.jar"), "org/apache/karaf/shell/wrapper/Main.class");

      System.out.println("");
      System.out.println("Setup complete.  You may wish to tweak the JVM properties in the wrapper configuration file:");
      System.out.println("\t" + wrapperConf.getPath());
      System.out.println("before installing and starting the service.");
      System.out.println("");

      if (os.startsWith("Win")) {
        new WindowsUsage().printUsage(this);
      } else if (os.startsWith("Mac OS X")) {
        new MacOSUsage().printUsage(this);
      } else if (os.startsWith("Linux")) {
        new LinuxUsage().printUsage(this);
      }

    } catch (Exception e) {
      e.printStackTrace();
      throw e;
    }
  }

  public Map<String,String> getDefaultWrapperProperties() {
    HashMap<String, String> props = new HashMap<String, String>();
    props.put("${karaf.java.home}", System.getProperty("KARAF_JAVA_HOME"));
    props.put("${karaf.home}", getKarafHome());
    props.put("${karaf.base}", getKarafBase());
    props.put("${karaf.data}", getKarafData());
    props.put("${name}", getName());
    props.put("${displayName}", getDisplayName());
    props.put("${description}", getDescription());
    props.put("${startType}", getStartType());

    return props;
  }

  public void setKarafBase(final String karafBase) {
    this.karafBase = karafBase;
  }

  public String getKarafBase() {
    return karafBase;
  }

  public String getKarafHome() {
    if (karafHome == null) {
      karafHome = getKarafBase();
    }
    return karafHome;
  }

  public void setKarafHome(final String karafHome) {
    this.karafHome = karafHome;
  }

  public String getKarafData() {
    if (karafData == null) {
      karafData = new File(getKarafBase(), "data").getAbsolutePath();
    }
    return karafData;
  }

  public void setKarafData(final String karafData) {
    this.karafData = karafData;
  }

  public String getName() {
    if (name == null) {
      File base = new File(System.getProperty("karaf.base"));
      name = base.getName();
    }
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDisplayName() {
    if (displayName == null) {
      displayName = getName();
    }
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getStartType() {
    return startType;
  }

  public void setStartType(String startType) {
    this.startType = startType;
  }

  public File getServiceFile() {
    return serviceFile;
  }

  public void setServiceFile(File serviceFile) {
    this.serviceFile = serviceFile;
  }

  public File getWrapperConf() {
    return wrapperConf;
  }

  public void setWrapperConf(File wrapperConf) {
    this.wrapperConf = wrapperConf;
  }
}
