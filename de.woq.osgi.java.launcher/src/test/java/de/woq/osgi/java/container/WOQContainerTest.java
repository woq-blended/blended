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

package de.woq.osgi.java.container;

import org.junit.Assert;
import org.junit.Test;

public class WOQContainerTest {

  @Test
  public void containerParamTest() {

    CommandLineParser clp = new CommandLineParser("common");
    WOQContainer container = new WOQContainer(clp.getParsedProperties(), clp.getContainerName());

    Assert.assertEquals("common", container.getContainerName());

    Assert.assertEquals("128m", container.getMinHeap());
    Assert.assertEquals("128m", container.getMaxHeap());
    Assert.assertEquals("256k", container.getStackSize());

    Assert.assertTrue(container.getJvmOptions().contains("-Xdebug"));
    Assert.assertTrue(container.getJvmOptions().contains("jmxremote"));

  }
}
