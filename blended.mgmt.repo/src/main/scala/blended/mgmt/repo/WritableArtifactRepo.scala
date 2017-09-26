package blended.mgmt.repo

import scala.util.Try
import java.io.InputStream

trait WritableArtifactRepo extends ArtifactRepo {

  def uploadFile(path: String, file: InputStream, sha1Sum: Option[String]): Try[Unit]

}