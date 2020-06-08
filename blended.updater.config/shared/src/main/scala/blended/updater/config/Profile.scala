package blended.updater.config

case class Profile(name: String, version: String) {
  override def toString(): String = s"${getClass().getSimpleName()}(name=${name},version=${version})}"
}
