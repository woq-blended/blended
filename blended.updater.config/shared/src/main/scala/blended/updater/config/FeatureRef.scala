package blended.updater.config

case class FeatureRef(
  url : String,
  names : List[String]
) {

  override def toString(): String =
    getClass().getSimpleName() +
      "(url=" + url + ",names=" + names.mkString("[", ",", "]") + ")"
}
