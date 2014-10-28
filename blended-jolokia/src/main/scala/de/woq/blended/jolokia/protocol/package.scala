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

package de.woq.blended.jolokia

package object protocol {

  trait MBeanSearchDef {
    def jmxDomain : String
    def searchProperties : Map[String, String] = Map.empty

    def pattern = searchProperties match {
      case m if m.isEmpty => ""
      case m => m.keys.map( k => s"${k}=${m.get(k).get}" ).mkString("", ",", ",")
    }
  }

  trait OperationExecDef {
    def objectName    : String
    def operationName : String
    def parameters    : List[String] = List.empty

    def pattern = s"${objectName}/${operationName}/" + parameters.mkString("/")
  }
  
  case object GetJolokiaVersion
  case class  SearchJolokia(searchDef : MBeanSearchDef)
  case class  ReadJolokiaMBean(objectName: String)
  case class  ExecJolokiaOperation(execDef: OperationExecDef)
}
