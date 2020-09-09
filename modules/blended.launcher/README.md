# OSGi Launcher

Launch an OSGi Framework.

## Invocation

```bash
java -cp launcher.jar:org.osgi.core-5.0.0.jar:com.typesafe.config-1.2.1.jar blended.launcher.Launcher configfile
```

## Configuration

```conf
blended.launcher.Launcher {
  # configuration goes here
}
```

Please refer to the full config reference at
[src/main/binaryResources/de/wayofquality/blended/launcher/LauncherConfig-reference.conf]()

Example(s):

- [src/test/binaryResources/example-config]()
