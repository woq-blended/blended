package blended.persistence.jdbc

import java.{ util => ju }

/**
 * A schema-less persisted entity class, consisting of many fields.
 */
case class PersistedClass(
  id: Option[Long],
  name: String,
  fields: Seq[PersistedField]
)
