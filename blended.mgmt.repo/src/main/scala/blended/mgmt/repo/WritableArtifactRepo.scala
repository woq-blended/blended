package blended.mgmt.repo

import java.io.InputStream

import scala.util.Try

trait WritableArtifactRepo extends ArtifactRepo {

  /**
   * Upload the file given as [[InputStream]] as `path`.
   * If the optional `sha1Sum` is given, it file contents checksum will be checked against it,
   * and a checksum mismatch will result in returning a [[scala.util.Failure]].
   */
  def uploadFile(path: String, file: InputStream, sha1Sum: Option[String]): Try[Unit]

}