package blended.updater.config

import upickle.Js

object JsonProtocol {

  private[this] def jsValue(prop: String, values: Map[String, Js.Value]) : Js.Value = {
    values.get(prop) match {
      case None => throw new SerializationException(s"JSON [$values] is missing mandatory field [${prop}]")
      case (Some(value)) => value
    }
  }

}
