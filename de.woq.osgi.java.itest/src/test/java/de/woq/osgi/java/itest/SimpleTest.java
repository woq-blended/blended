/*
 * Copyright 2013, WoQ - Way of Quality UG(mbH)
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package de.woq.osgi.java.itest;

import de.woq.osgi.java.container.id.ContainerIdentifierService;
import de.woq.osgi.java.itestsupport.AbstractWOQContainerTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class SimpleTest extends AbstractWOQContainerTest {

  private final static Logger LOGGER = LoggerFactory.getLogger(SimpleTest.class);

  @Inject
  ContainerIdentifierService idService;

  @Configuration
  public Option[] config() throws Exception {
    return containerConfiguration();
  }

  @Test
  public void containerIDTest() {
    Assert.assertNotNull(idService);
    LOGGER.info("Started container [{}]", idService.getUUID());
  }

}
