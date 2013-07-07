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

package de.woq.osgi.java.osgidep.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import de.woq.osgi.java.osgidep.BundleDetails;
import de.woq.osgi.java.osgidep.BundleFilter;
import de.woq.osgi.java.osgidep.DependencyAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DOTFileGenerator
{

  private DependencyAnalyzer analyzer = null;
  private List<GeneratorInfo> generatorInfo = new ArrayList<GeneratorInfo>();

  private static final int ANALYSIS_DELAY = 10;
  private Logger log = LoggerFactory.getLogger(DOTFileGenerator.class);

  public List<GeneratorInfo> getGeneratorInfo()
  {
    return generatorInfo;
  }

  public void setGeneratorInfo(List<GeneratorInfo> generatorInfo)
  {
    this.generatorInfo = generatorInfo;
  }

  public DependencyAnalyzer getAnalyzer()
  {
    return analyzer;
  }

  public void setAnalyzer(DependencyAnalyzer analyzer)
  {
    this.analyzer = analyzer;
  }

  public void start()
  {
    log.info("Starting Dependency Analyzer ...");

    final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);

    executor.schedule(new Runnable()
    {
      @Override
      public void run()
      {
        analyze();
      }
    }, ANALYSIS_DELAY, TimeUnit.SECONDS);
  }

  private void analyze()
  {
    for (GeneratorInfo info : getGeneratorInfo())
    {
      final VelocityRunner runner = new VelocityRunner();
      final BundleFilter bf = new BundleFilter(info.getFilters());

      final Map<Long, BundleDetails> bundles = analyzer.analyze(bf);

      runner.addProperty("system", System.getProperties());
      runner.addProperty("bundleDetails", bundles);

      runner.run("templates/" + info.getTemplateName() + ".vm", info.getTargetName());
      log.info(String.format("File generated in [%s].", info.getTargetName()));
    }
  }
}
