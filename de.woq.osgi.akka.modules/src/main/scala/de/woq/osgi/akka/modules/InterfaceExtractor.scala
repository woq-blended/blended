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

package de.woq.osgi.akka.modules

class InterfaceExtractor(clazz : Class[_]) {

  private def allInterfacesTR(interfaces: List[Class[_]], result: List[Class[_]]) : List[Class[_]] =
    interfaces match {
      case Nil => result
      case _ => {
        allInterfacesTR(interfaces flatMap ( x => x.getInterfaces ), interfaces ::: result)
      }
    }

  private def interfacesForClass(clazz : Class[_]) : List[Class[_]] = {

    if (clazz == classOf[Object])
      Nil
    else {
      (interfacesForClass(clazz.getSuperclass) ::: allInterfacesTR(clazz.getInterfaces.toList, Nil)).distinct
    }
  }

  val interfaces : Array[Class[_]] = {

    val clazzes = interfacesForClass(clazz)

    if (clazzes.isEmpty) Array(clazz) else clazzes.toArray
  }
}
