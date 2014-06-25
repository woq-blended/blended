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

package de.woq.blended.camel.utils;

import org.apache.camel.Exchange;
import org.apache.camel.builder.SimpleBuilder;

public class ExpressionURIFactory implements EndpointURIFactory {

  private final String expression;

  public ExpressionURIFactory(String expression) {
    this.expression = expression;
  }

  @Override
  public String[] createEndpointUris(Exchange exchange) throws Exception {
    SimpleBuilder builder = new SimpleBuilder(expression);
    return new String[] { builder.evaluate(exchange, String.class) };
  }
}
