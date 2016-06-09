import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

Model(
  "de.wayofquality.blended" % "blended.parent" % "2.0-SNAPSHOT",
  packaging = "pom",
  name = "${project.artifactId}",
  description = "Common Settings and configurations for all blended modules.",
  url = "https://github.com/woq-blended/blended",
  developers = Seq(
    Developer(
      email = "andreas@wayofquality.de",
      name = "Andreas Gies",
      organization = "WoQ - Way of Quality GmbH",
      organizationUrl = "http://www.wayofquality.de"
    ),
    Developer(
      email = "tobias.roeser@tototec.de",
      name = "Tobias Roeser",
      organization = "ToToTec GbR"
    )
  ),
  licenses = Seq(
    License(
      name = "The Apache License, Version 2.0",
      url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
    )
  ),
  scm = Scm(
    connection = "scm:git:ssh://git@github.com/woq-blended/blended",
    developerConnection = "scm:git:ssh://git@github.com/woq-blended/blended.git",
    url = "https://github.com/woq-blended/blended"
  ),
  organization = Organization(
    name = "https://github.com/woq-blended",
    url = "https://github.com/woq-blended/blended"
  ),
  repositories = Seq(
    Repository(
      releases = RepositoryPolicy(
        enabled = true
      ),
      snapshots = RepositoryPolicy(
        enabled = false
      ),
      id = "FUSEStaging",
      url = "http://repo.fusesource.com/nexus/content/repositories/jboss-fuse-6.1.x"
    ),
    Repository(
      releases = RepositoryPolicy(
        enabled = true
      ),
      snapshots = RepositoryPolicy(
        enabled = false
      ),
      id = "SpringBundles",
      url = "http://repository.springsource.com/maven/bundles/release"
    ),
    Repository(
      releases = RepositoryPolicy(
        enabled = true
      ),
      snapshots = RepositoryPolicy(
        enabled = false
      ),
      id = "SpringExternalBundles",
      url = "http://repository.springsource.com/maven/bundles/external"
    )
  ),
  dependencies = Seq(
    "junit" % "junit" % "${junit.version}" % "test"
  ),
  dependencyManagement = DependencyManagement(
    dependencies = Seq(
      "org.scala-lang" % "scala-library" % "${scala.version}.${scala.micro.version}",
      "org.scala-lang" % "scala-reflect" % "${scala.version}.${scala.micro.version}",
      "org.slf4j" % "slf4j-api" % "${slf4j.version}",
      "org.apache.activemq" % "activemq-osgi" % "${activemq.version}",
      "org.scalatest" % "scalatest_${scala.version}" % "${scalatest.version}",
      "com.github.domino-osgi" % "domino_${scala.version}" % "${domino.version}"
    )
  ),
  distributionManagement = DistributionManagement(
    repository = DeploymentRepository(
      id = "ossrh",
      url = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
    ),
    snapshotRepository = DeploymentRepository(
      id = "ossrh",
      url = "https://oss.sonatype.org/content/repositories/snapshots/"
    )
  ),
  properties = Map(
    "aries.util.version" -> "1.1.0",
    "bndtool.version" -> "3.2.0",
    "mimepull.version" -> "1.9.4",
    "hamcrest.version" -> "1.3.0.1",
    "spring.version" -> "3.2.14.RELEASE",
    "felix.ca.version" -> "1.8.6",
    "project.build.sourceEncoding" -> "UTF-8",
    "shiro.version" -> "1.2.4",
    "docker.host" -> "localhost",
    "commons.lang.version" -> "2.6",
    "aries.blueprint.version" -> "1.0.1",
    "felix.metatype.version" -> "1.0.12",
    "cmdoption.version" -> "0.4.2",
    "felix.event.version" -> "1.3.2",
    "activeio.version" -> "3.1.4",
    "felix.gogo.shell.version" -> "0.10.0",
    "osgi.enterprise.version" -> "4.2.0",
    "felix.framework.version" -> "5.0.0",
    "logback.version" -> "1.1.3",
    "cglib.version" -> "2.2.0",
    "mockito.version" -> "1.9.5",
    "aries.proxy.version" -> "1.0.1",
    "scala.micro.version" -> "8",
    "joda-time.version" -> "1.6.2",
    "commons.pool.version" -> "1.6",
    "commons.exec.version" -> "1.3",
    "felix.fileinstall.version" -> "3.4.2",
    "insight-log.version" -> "7.2.0.redhat-060",
    "osgi.core.version" -> "4.3.0",
    "log4j.version" -> "1.2.15",
    "jettison.version" -> "1.3.4",
    "commons.discovery.version" -> "0.4.0",
    "felix.gogo.runtime.version" -> "0.16.2",
    "spray-json.version" -> "1.3.1",
    "xbean.finder.version" -> "3.16",
    "scalatest.version" -> "2.2.4",
    "commons.httpclient.version" -> "3.1.0",
    "commons.net.version" -> "3.3",
    "docker.port" -> "4243",
    "felix.gogo.command.version" -> "0.14.0",
    "docker-java.version" -> "1.0.0",
    "pax-swissbox.version" -> "1.7.0",
    "loglevel.test" -> "INFO",
    "jasypt.version" -> "1.9.0_1",
    "stax.api.version" -> "2.2.0",
    "xbean.version" -> "3.16",
    "camel.version" -> "2.16.3",
    "ops4j-base.version" -> "1.4.0",
    "uncommons.maths.version" -> "1.2.2",
    "jsch.version" -> "0.1.48_1",
    "blended.version" -> "2.0-SNAPSHOT",
    "aries.jmx.version" -> "1.1.1",
    "jersey.version" -> "1.18.1",
    "slf4j.version" -> "1.7.12",
    "felix.scr.version" -> "1.6.2",
    "aries.blueprint.core.version" -> "1.4.3",
    "jledit.version" -> "0.2.0",
    "commons.io.version" -> "1.4.0",
    "jackson.version" -> "1.9.12",
    "akka.version" -> "2.3.10",
    "ow2.asm.all.version" -> "4.1",
    "hawtio.version" -> "1.4.65",
    "javax.mail.version" -> "1.4.5",
    "orientdb.version" -> "2.2.0",
    "xmlbeans.version" -> "2.4.0",
    "protobuf.version" -> "2.5.0",
    "shapeless.version" -> "1.2.4",
    "netty.version" -> "3.8.1.Final",
    "neo4j.version" -> "2.1.2",
    "commons.beanutils.version" -> "1.8.3_2",
    "domino.version" -> "1.1.1",
    "spring.osgi.version" -> "1.2.1",
    "typesafe.config.version" -> "1.2.1",
    "felix.cm.version" -> "1.6.0",
    "scala.version" -> "2.11",
    "compiler-plugin.version" -> "2.5.1",
    "parboiled.version" -> "1.1.6",
    "pax-web.version" -> "3.1.0",
    "war.plugin.version" -> "2.6",
    "bundle.plugin.version" -> "3.0.1",
    "xbean.asm4.version" -> "3.16",
    "junit.version" -> "4.11",
    "commons.collections.version" -> "3.2.1",
    "jolokia.version" -> "1.3.3",
    "activemq.version" -> "5.13.3",
    "json-lenses.version" -> "0.5.4",
    "osweb.asm.version" -> "3.1.0",
    "spray.version" -> "1.3.2",
    "jetty.version" -> "7.6.8.v20121106",
    "commons.codec.version" -> "1.6.0",
    "linkedhashmap.version" -> "1.4.2",
    "java.version" -> "1.7"
  ),
  build = Build(
    resources = Seq(
      Resource(
        filtering = true,
        directory = "src/main/resources"
      ),
      Resource(
        directory = "src/main/binaryResources"
      )
    ),
    testResources = Seq(
      Resource(
        filtering = true,
        directory = "src/test/resources"
      ),
      Resource(
        directory = "src/test/binaryResources"
      )
    ),
    pluginManagement = PluginManagement(
      plugins = Seq(
        Plugin(
          "org.apache.maven.plugins" % "maven-war-plugin" % "${war.plugin.version}",
          configuration = Config(
            archive = Config(
              manifestFile = "${project.build.outputDirectory}/META-INF/MANIFEST.MF"
            )
          )
        ),
        Plugin(
          "org.codehaus.mojo" % "build-helper-maven-plugin" % "1.9.1",
          executions = Seq(
            Execution(
              id = "add-scala-sources",
              phase = "generate-sources",
              goals = Seq(
                "add-source"
              ),
              configuration = Config(
                sources = Config(
                  source = "src/main/scala"
                )
              )
            ),
            Execution(
              id = "add-scala-test-sources",
              phase = "generate-sources",
              goals = Seq(
                "add-test-source"
              ),
              configuration = Config(
                sources = Config(
                  source = "src/test/scala"
                )
              )
            )
          )
        ),
        Plugin(
          "org.apache.maven.plugins" % "maven-source-plugin" % "2.4"
        ),
        Plugin(
          "org.apache.maven.plugins" % "maven-source-plugin" % "2.4",
          executions = Seq(
            Execution(
              id = "attach-sources-no-fork",
              phase = "generate-sources",
              goals = Seq(
                "jar-no-fork"
              )
            ),
            Execution(
              id = "attach-sources",
              phase = "DISABLE_FORKED_LIFECYCLE_MSOURCES-13",
              goals = Seq(
                "jar-no-fork"
              )
            )
          ),
          configuration = Config(
            includes = Config(
              include = "**/*.java",
              include = "**/*.scala"
            )
          )
        ),
        Plugin(
          "org.apache.maven.plugins" % "maven-enforcer-plugin" % "1.3.1",
          executions = Seq(
            Execution(
              id = "enforce-maven",
              goals = Seq(
                "enforce"
              ),
              configuration = Config(
                rules = Config(
                  requireMavenVersion = Config(
                    version = "3.0.5"
                  )
                )
              )
            )
          )
        ),
        Plugin(
          "org.apache.maven.plugins" % "maven-gpg-plugin" % "1.6",
          executions = Seq(
            Execution(
              id = "sign-artifacts",
              phase = "verify",
              goals = Seq(
                "sign"
              )
            )
          )
        ),
        Plugin(
          "org.apache.maven.plugins" % "maven-antrun-plugin" % "1.8"
        ),
        Plugin(
          "org.apache.maven.plugins" % "maven-dependency-plugin" % "2.6"
        ),
        Plugin(
          "org.apache.maven.plugins" % "maven-assembly-plugin" % "2.4"
        ),
        Plugin(
          "org.apache.maven.plugins" % "maven-shade-plugin" % "2.3"
        ),
        Plugin(
          "org.apache.felix" % "maven-bundle-plugin" % "${bundle.plugin.version}",
          extensions = true,
          executions = Seq(
            Execution(
              id = "manifest",
              phase = "process-classes",
              goals = Seq(
                "manifest"
              )
            )
          ),
          dependencies = Seq(
            "biz.aQute.bnd" % "biz.aQute.bndlib" % "${bndtool.version}"
          ),
          configuration = Config(
            supportedProjectTypes = Config(
              supportedProjectType = "jar",
              supportedProjectType = "bundle",
              supportedProjectType = "war"
            ),
            instructions = Config(
              _include = "osgi.bnd"
            )
          )
        ),
        Plugin(
          "org.apache.maven.plugins" % "maven-compiler-plugin" % "${compiler-plugin.version}",
          configuration = Config(
            source = "${java.version}",
            target = "${java.version}",
            encoding = "${project.build.sourceEncoding}",
            fork = "true"
          )
        ),
        Plugin(
          "org.apache.maven.plugins" % "maven-surefire-plugin" % "2.12",
          executions = Seq(
            Execution(
              id = "default-test",
              phase = "test",
              goals = Seq(
                "test"
              )
            )
          ),
          dependencies = Seq(
            "org.apache.maven.surefire" % "surefire-junit47" % "2.12"
          ),
          configuration = Config(
            forkMode = "always",
            forkedProcessTimeoutInSeconds = "300",
            argLine = "-Xmx1024m"
          )
        ),
        Plugin(
          "net.alchim31.maven" % "scala-maven-plugin" % "3.2.1",
          executions = Seq(
            Execution(
              id = "compile-scala",
              goals = Seq(
                "compile"
              ),
              configuration = Config(
                fork = "true",
                recompileMode = "incremental",
                useZincServer = "true",
                addJavacArgs = "-target|${java.version}|-source|${java.version}",
                addZincArgs = "-C-target|-C${java.version}|-C-source|-C${java.version}",
                args = Config(
                  arg = "-deprecation",
                  arg = "-feature",
                  arg = "-Xlint",
                  arg = "-Ywarn-nullary-override"
                ),
                jvmArgs = Config(
                  jvmArg = "-Xms256m",
                  jvmArg = "-Xmx512m",
                  jvmArg = "-XX:MaxPermSize=128m"
                )
              )
            ),
            Execution(
              id = "test-compile-scala",
              goals = Seq(
                "testCompile"
              ),
              configuration = Config(
                fork = "true",
                recompileMode = "incremental",
                useZincServer = "true",
                addJavacArgs = "-target|${java.version}|-source|${java.version}",
                addZincArgs = "-C-target|-C${java.version}|-C-source|-C${java.version}",
                args = Config(
                  arg = "-deprecation",
                  arg = "-feature",
                  arg = "-Xlint",
                  arg = "-Ywarn-nullary-override"
                ),
                jvmArgs = Config(
                  jvmArg = "-Xms256m",
                  jvmArg = "-Xmx512m",
                  jvmArg = "-XX:MaxPermSize=128m"
                )
              )
            )
          ),
          configuration = Config(
            scalaVersion = "${scala.version}.${scala.micro.version}"
          )
        ),
        Plugin(
          "com.alexecollins.docker" % "docker-maven-plugin" % "2.4.0",
          executions = Seq(
            Execution(
              id = "clean-docker",
              phase = "clean",
              goals = Seq(
                "clean"
              )
            ),
            Execution(
              id = "package-docker",
              phase = "package",
              goals = Seq(
                "package"
              )
            ),
            Execution(
              id = "deploy-docker",
              phase = "deploy",
              goals = Seq(
                "deploy"
              )
            )
          ),
          configuration = Config(
            version = "1.16",
            username = "atooni",
            email = "andreas@wayofquality.de",
            password = "foo",
            prefix = "blended",
            host = "http://${docker.host}:${docker.port}"
          )
        ),
        Plugin(
          "org.scalatest" % "scalatest-maven-plugin" % "1.0",
          executions = Seq(
            Execution(
              id = "test",
              goals = Seq(
                "test"
              )
            )
          ),
          configuration = Config(
            reportsDirectory = "${project.build.directory}/surefire-reports",
            junitxml = ".",
            stdout = "FT"
          )
        )
      )
    ),
    plugins = Seq(
      Plugin(
        "org.codehaus.mojo" % "build-helper-maven-plugin" % "1.9.1"
      )
    )
  ),
  profiles = Seq(
    Profile(
      id = "baseline",
      build = BuildBase(
        plugins = Seq(
          Plugin(
            "org.apache.felix" % "maven-bundle-plugin" % "${bundle.plugin.version}",
            extensions = true,
            executions = Seq(
              Execution(
                id = "baseline",
                phase = "verify",
                goals = Seq(
                  "baseline"
                )
              )
            ),
            dependencies = Seq(
              "biz.aQute.bnd" % "biz.aQute.bndlib" % "${bndtool.version}"
            ),
            configuration = Config(
              supportedProjectTypes = Config(
                supportedProjectType = "jar",
                supportedProjectType = "bundle",
                supportedProjectType = "war"
              ),
              instructions = Config(
                _include = "osgi.bnd"
              )
            )
          )
        )
      )
    ),
    Profile(
      id = "release",
      activation = Activation(
      ),
      build = BuildBase(
        plugins = Seq(
          Plugin(
            "org.apache.maven.plugins" % "maven-source-plugin"
          ),
          Plugin(
            "org.apache.maven.plugins" % "maven-gpg-plugin"
          ),
          Plugin(
            "net.alchim31.maven" % "scala-maven-plugin",
            executions = Seq(
              Execution(
                id = "attach-doc",
                goals = Seq(
                  "doc-jar"
                )
              )
            )
          ),
          Plugin(
            "org.sonatype.plugins" % "nexus-staging-maven-plugin" % "1.6.5",
            extensions = true,
            configuration = Config(
              serverId = "ossrh",
              nexusUrl = "https://oss.sonatype.org/",
              autoReleaseAfterClose = "true"
            )
          )
        )
      )
    )
  ),
  modelVersion = "4.0.0"
)
