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

package de.woq.blended.itestsupport.jmx

import javax.management.remote.JMXServiceURL

trait JMXUrlProvider {
  def serviceUrl : JMXServiceURL
}

trait KarafJMXUrlProvider extends JMXUrlProvider {

  private var host = "localhost"
  private var port = 1099

  def withHost(h: String) = { host = h; this }
  def withHost(p: Int)    = { port = p; this }

  override def serviceUrl =
    new JMXServiceURL(s"service:jmx:rmi:///jndi/rmi://${host}:${port}/jmxrmi")
}