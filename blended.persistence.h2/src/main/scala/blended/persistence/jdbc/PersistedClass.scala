package blended.persistence.jdbc

import java.{ util => ju }

case class PersistedClass(
  id: Option[Long],
  name: String,
  fields: Seq[PersistedField]
)
