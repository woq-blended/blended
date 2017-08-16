package blended.mgmt.repo

import java.io.File
import scala.collection.immutable

trait ArtifactRepo {
  
  /**
   * The id (or name) of this repository.
   */
  def repoId: String

  /**
   * Find the file associated with the given artifact path.
   * @return A `Some` of the file or `None` if the artifact does not exists in the repository.
   */
  def findFile(path: String): Option[File]

  /**
   * Find the SHA1 checksum of the given artifact path.
   * @return A `Some` of the checksum or `None`if the artifact does not exists in the repository.
   */
  def findFileSha1Checksum(path: String): Option[String]
  
  /**
   * Find all known files (recursive) under the given artifact path (with their relative path).
   */
  def listFiles(path: String): Iterator[String]

}