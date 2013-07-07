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

package de.woq.osgi.java.startcompletion.internal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import de.woq.osgi.java.startcompletion.StartCompletionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StartCompletionServiceImpl implements StartCompletionService
{

  private static final Logger LOG = LoggerFactory.getLogger(StartCompletionServiceImpl.class);

  private List<String> completedTokens = new ArrayList<String>();
  private List<CompletionLatch> latches = new ArrayList<CompletionLatch>();

  private Object semaphore = new Object();

  @Override
  public void complete(String token)
  {
    synchronized (semaphore)
    {
      LOG.info("Completing token [" + token + "]");
      if (completedTokens.contains(token))
      {
        return;
      }

      for (Iterator<CompletionLatch> it = latches.iterator(); it.hasNext(); )
      {
        CompletionLatch latch = it.next();

        if (latch.complete(token))
        {
          it.remove();
        }
      }
      completedTokens.add(token);
    }
  }

  @Override
  public void waitForTokens(String id, long time, TimeUnit unit, String... tokens)
  {
    LOG.info("Initialising wait [" + id + "]");

    List<String> pendingTokens = new ArrayList<String>();
    CompletionLatch latch;

    synchronized (semaphore)
    {
      if (tokens == null || tokens.length == 0)
      {
        return;
      }

      for (String token : tokens)
      {
        if (!completedTokens.contains(token))
        {
          pendingTokens.add(token);
        }
      }

      LOG.debug("Tokens to be completed: " + pendingTokens);
      if (pendingTokens.isEmpty())
      {
        LOG.info("Finished Wait [" + id + "]");
        return;
      }

      latch = new CompletionLatch(id, pendingTokens);
      latches.add(latch);
    }

    try
    {
      latch.await(time, unit);
    }
    catch (InterruptedException ie)
    {
      Thread.currentThread().interrupt();
    }
    finally
    {
      if (!latch.isCompleted())
      {
        LOG.warn("Wait for latch [" + id + "] timed out.");
      }
      LOG.debug("Finished Wait [" + id + "], remaining tokens: " + latch.tokens);
      synchronized (semaphore)
      {
        latches.remove(latch);
      }
    }
  }

  private static class CompletionLatch
  {
    private final CountDownLatch latch = new CountDownLatch(1);
    private final String id;
    private final List<String> tokens;
    private AtomicBoolean completed = new AtomicBoolean(false);

    CompletionLatch(String id, List<String> tokens)
    {
      this.id = id;
      this.tokens = tokens;
    }

    void await(long time, TimeUnit unit) throws InterruptedException
    {
      latch.await(time, unit);
    }

    boolean complete(String token)
    {
      LOG.debug(String.format("Received token [%s] for [%s], remaining tokens [%s].", token, id, tokens.toString()));

      if (tokens.contains(token))
      {
        tokens.remove(token);
        if (tokens.isEmpty())
        {
          LOG.debug("Token list [" + id + "] done. Notifying thread.");
          latch.countDown();
        }
      }

      completed.set(tokens.isEmpty());
      return completed.get();
    }

    public boolean isCompleted()
    {
      return completed.get();
    }
  }
}
