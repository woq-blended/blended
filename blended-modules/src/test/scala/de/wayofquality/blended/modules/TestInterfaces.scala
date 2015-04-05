/*
 * Copyright 2014ff, WoQ - Way of Quality GmbH
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

package de.wayofquality.blended.modules

class TestClass1
class TestClass2 extends TestInterface2
class TestClass3 extends TestInterface2 with TestInterface3
class TestClass4 extends TestInterface4

trait TestInterface1 {
  def name = getClass.getName
}

trait TestInterface2
trait TestInterface3
trait TestInterface4 extends TestInterface4a
trait TestInterface4a extends TestInterface4b
trait TestInterface4b extends TestInterface4c
trait TestInterface4c

class TestClass5 extends TestInterface2 { this: TestInterface1 =>
}
