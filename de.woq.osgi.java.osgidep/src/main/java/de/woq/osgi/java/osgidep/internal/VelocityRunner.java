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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VelocityRunner
{

  private VelocityContext context = new VelocityContext();
  private Logger log = LoggerFactory.getLogger(VelocityRunner.class);
  private VelocityEngine engine = new VelocityEngine();

  public VelocityRunner()
  {
    try
    {
      engine.init();
    }
    catch (Exception e)
    {
      // do nothing
    }
  }

  public void addProperty(String key, Object value)
  {
    context.put(key, value);
  }

  public void run(String tplFile, String outFileName)
  {

    final File outputDirectory = new File(System.getProperty("whiteboard.home", System.getProperty("user.home")));

    BufferedWriter out = null;
    BufferedReader in = null;

    try
    {
      final InputStream is = this.getClass().getClassLoader().getResourceAsStream(tplFile);
      if (is != null)
      {
        in = new BufferedReader(new InputStreamReader(is));
        final File outFile = new File(outputDirectory.getAbsoluteFile(), outFileName);
        outFile.getParentFile().mkdirs();
        out = new BufferedWriter(new FileWriter(outFile));
        engine.evaluate(context, out, "Dependency Analyzer", in);
      }
    }
    catch (Exception e)
    {
      log.error("Failed to run Velocity template [" + tplFile + "]", e);
    }
    finally
    {
      try
      {
        if (out != null)
        {
          out.flush();
          out.close();
        }
      }
      catch (IOException ioe)
      {
        // do nothing
      }
      try
      {
        if (in != null)
        {
          in.close();
        }
      }
      catch (IOException ioe)
      {
        // do nothing
      }
    }
  }
}
