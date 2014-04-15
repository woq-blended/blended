/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.woq.osgi.spray.servlet

import javax.servlet.{ ServletContextListener, ServletContextEvent }
import akka.util.Switch
import spray.servlet.{ConnectorSettings, Initializer}
import de.woq.osgi.akka.system.BundleName
import com.typesafe.config.ConfigFactory
import java.io.File
import spray.http.Uri

trait SprayOSGiInitializer extends ServletContextListener { this : BundleName =>

  private val booted = new Switch(false)

  def contextInitialized(ev: ServletContextEvent): Unit = {
    booted switchOn {
      val ctx = ev.getServletContext
      ctx.log("Starting spray application ...")

      require(OSGiConfigHolder.actorSystem != None)
      require(OSGiConfigHolder.actorRef != None)

      val config = ConfigFactory.parseFile(new File(System.getProperty("karaf.home") + "/etc", s"$bundleSymbolicName.conf"))

      val settings0 = ConnectorSettings(config)
      val settings =
        if (settings0.rootPath == Uri.Path("AUTO")) {
          ctx.log(s"Automatically setting spray.servlet.root-path to '${ctx.getContextPath}'")
          settings0.copy(rootPath = Uri.Path(ctx.getContextPath))
        } else settings0

      ctx.setAttribute(Initializer.SettingsAttrName, settings)
      ctx.setAttribute(Initializer.SystemAttrName, OSGiConfigHolder.actorSystem.get)
      ctx.setAttribute(Initializer.ServiceActorAttrName, OSGiConfigHolder.actorRef.get)
    }
  }

  def contextDestroyed(e: ServletContextEvent): Unit = {
    booted switchOff {
      e.getServletContext.log("Shutting down spray application ...")
    }
  }
}
