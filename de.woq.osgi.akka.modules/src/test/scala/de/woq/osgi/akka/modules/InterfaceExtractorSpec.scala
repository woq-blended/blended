package de.woq.osgi.akka.modules

import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.{Matchers, WordSpec}

/*
 * Copyright 2014, WoQ - Way of Quality UG(mbH)
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class InterfaceExtractorSpec extends WordSpec with Matchers with AssertionsForJUnit {

  "Interface extractor should " should {

    "resolve to the class of the given service object itself when it does not implement a service interface" in {

      val service = new TestClass1

      val extractor = new InterfaceExtractor(service.getClass)
      extractor.interfaces should not be (null)
      extractor.interfaces should have size(1)
      extractor.interfaces should contain(classOf[TestClass1])
    }

    "resolve to the service interface if the service object implements one interface" in {
      val service = new TestClass2

      val extractor = new InterfaceExtractor(service.getClass)
      extractor.interfaces should not be (null)
      extractor.interfaces should have size(1)
      extractor.interfaces should contain(classOf[TestInterface2])
    }

    "resolve to to all implemented interfaces of the service object" in {
      val service = new TestClass3

      val extractor = new InterfaceExtractor(service.getClass)
      extractor.interfaces should not be (null)
      extractor.interfaces should have size(2)
      extractor.interfaces should contain(classOf[TestInterface2])
      extractor.interfaces should contain(classOf[TestInterface3])
    }

    "resolve to to all interfaces in the inheritance chain of the service object" in {
      val service = new TestClass4

      val extractor = new InterfaceExtractor(service.getClass)
      extractor.interfaces should not be (null)
      extractor.interfaces should have size(4)
      extractor.interfaces should contain(classOf[TestInterface4a])
      extractor.interfaces should contain(classOf[TestInterface4b])
      extractor.interfaces should contain(classOf[TestInterface4c])
      extractor.interfaces should contain(classOf[TestInterface4])
    }

    "resolve implicit typing correctly" in {

      val service = new TestClass5 with TestInterface1
      val extractor = new InterfaceExtractor(service.getClass)

      extractor.interfaces should not be (null)
      extractor.interfaces should have size (2)
      extractor.interfaces should contain(classOf[TestInterface1])
      extractor.interfaces should contain(classOf[TestInterface2])
    }

  }

}


