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

import javax.inject.Inject;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.ops4j.pax.exam.CoreOptions.*;

@RunWith(PaxExam.class)
@ExamReactorStrategy
public class SimpleTest {

  private final static Logger LOGGER = LoggerFactory.getLogger(SimpleTest.class);

  @Inject
  private BundleContext context;

  @Configuration
  public Option[] config() {
    return options(
      mavenBundle().groupId("net.sourceforge.cglib").artifactId("com.springsource.net.sf.cglib").version("2.2.0").startLevel(2),
      junitBundles()
    );
  }

  @Test
  public void simpleTest() {
    LOGGER.info("Hello from my Test!");
    Assert.assertNotNull(context);

    for(Bundle b: context.getBundles()) {
      LOGGER.info(String.format("Installed bundle [%d] : [%s]", b.getBundleId(), b.getSymbolicName()));
    }
  }
}
