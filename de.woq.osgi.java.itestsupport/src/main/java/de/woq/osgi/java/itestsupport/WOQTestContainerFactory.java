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

package de.woq.osgi.java.itestsupport;

import org.kohsuke.MetaInfServices;
import org.ops4j.pax.exam.ExamSystem;
import org.ops4j.pax.exam.TestContainer;
import org.ops4j.pax.exam.TestContainerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@MetaInfServices
public class WOQTestContainerFactory implements TestContainerFactory {

  private final static Logger LOGGER = LoggerFactory.getLogger(WOQTestContainerFactory.class);
  @Override
  public TestContainer[] create(ExamSystem system) {

    String profile = "common";
    long delay = 5000l;

    try {
      WithComposite composite = getCompositeSpec();
      profile = composite.location();
      delay = composite.delay();
    } catch (Exception e) {
      LOGGER.info("No composite annotation on Factory class");
    }

    TestContainer container = new WOQTestContainer(profile, delay);

    return new TestContainer[] { container };
  }

  private WithComposite getCompositeSpec() throws Exception {

    WithComposite compositeSpec = getClass().getAnnotation(WithComposite.class);
    if (compositeSpec == null) {
      throw new Exception("No Annotation 'WithComposite' set on class [{" + getClass().getName() + "}].");
    }
    return compositeSpec;
  }
}
