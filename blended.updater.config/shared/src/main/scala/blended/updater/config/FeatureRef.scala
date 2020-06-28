package blended.updater.config

/**
  * A feature refence holds the url of an archive that contains one or more feature configurations
  * with the jar's features directory
  *
  * @param url
  * @param names
  */
case class FeatureRef(
  url : String,
  names : List[String]
) {

  override def toString(): String =
    getClass().getSimpleName() +
      "(url=" + url + ",names=" + names.map(s => "\"" + s + "\"").mkString("[", ",", "]") + ")"
}
