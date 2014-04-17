/*
 * Copyright 2014ff, WoQ - Way of Quality UG(mbH)
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

package de.woq.osgi.java.itestsupport.condition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConditionWaiter {

  private final static Logger LOGGER = LoggerFactory.getLogger(ConditionWaiter.class);

  private final static long DEFAULT_TIMEOUT = 10000l;
  private final static long DEFAULT_CHECK_INTERVAL = 500l;

  private ConditionWaiter() {}

  public static void waitOnCondition(final Condition condition) throws Exception {
    waitOnCondition(DEFAULT_TIMEOUT, DEFAULT_CHECK_INTERVAL, condition);
  }

  public static void waitOnCondition(
    final long timeout,
    final long interval,
    final Condition...conditions
  ) throws Exception {

    final List<Condition> conditionList = new ArrayList<>();
    conditionList.addAll(Arrays.asList(conditions));

    boolean satisfied = conditionList.size() == 0;

    final long started = System.currentTimeMillis();

    while(!satisfied && System.currentTimeMillis() - started <= timeout) {

      while(conditionList .size() > 0) {
        Condition first = conditionList.get(0);
        LOGGER.info("Checking condition [{}]", first);

        if (first.satisfied()) {
          conditionList.remove(0);
        } else {
          break;
        }
      }

      if (conditionList.isEmpty()) {
        satisfied = true;
        break;
      } else {
        try {
          Thread.sleep(interval);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
      }
    }

    if (!satisfied) {
      StringBuffer sb = new StringBuffer();
      sb.append("The following conditions could not be satisfied:\n");
      for(Condition c: conditionList) {
        sb.append(c.toString() + "\n");
      }
      throw new Exception(sb.toString());
    }
  }
}
