package blended.persistence.jdbc

/**
 * A schema-less persisted entity class, consisting of many fields.
 */
case class PersistedClass(
  id: Option[Long],
  name: String,
  fields: Seq[PersistedField]
)
