package blended.updater.config

case class FeatureRef(
    name: String,
    version: String,
    url: Option[String] = None
) {

  override def toString(): String =
    getClass().getSimpleName() +
      "(name=" + name +
      ",version=" + version +
      ",url=" + url +
      ")"

}
