/*
 * Copyright 2014ff,  https://github.com/woq-blended
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

package blended.itestsupport.jmx

import javax.management.remote.JMXServiceURL

trait JMXUrlProvider {
  def serviceUrl : JMXServiceURL
}

case class KarafJMXUrlProvider(host: String = "localhost", port: Integer = 1099) extends JMXUrlProvider {

  def withHost(h: String) = copy( host = h )
  def withHost(p: Int)    = copy( port = p )

  override def serviceUrl =
    new JMXServiceURL(s"service:jmx:rmi:///jndi/rmi://$host:$port/jmxrmi")
}