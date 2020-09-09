package blended.updater.config

/** A reference to a profile, containing only the name and the version. */
case class ProfileRef(name: String, version: String) {
  override def toString(): String =
    getClass().getSimpleName() +
      "(name=" + name +
      ",version=" + version +
      ")"
}
