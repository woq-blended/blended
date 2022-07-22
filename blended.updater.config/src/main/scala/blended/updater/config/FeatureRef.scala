package blended.updater.config

/**
 * A feature refence holds the url of an archive that contains one or more feature configurations
 * with the jar's features directory
 *
 * @param url
 * @param names
 */
case class FeatureRef(
                       url: String,
                       names: List[String]
                     ) {

  val repoKeys: List[String] = names.map(n => s"$url#$n")

  override def toString(): String =
    getClass().getSimpleName() +
      "(url=" + url + ",names=" + names.sorted.map(s => "\"" + s + "\"").mkString("[", ",", "]") + ")"

  override def equals(other: Any): Boolean = {
    other match {
      case ref: FeatureRef => url == ref.url && names.sorted == ref.names.sorted
      case _ => false
    }
  }
}
