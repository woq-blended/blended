package blended.updater.config

import java.io.File

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

case class ProfileLookup(
  profileName: String,
  profileVersion: String,
  profileBaseDir: File)

object ProfileLookup {
  def read(config: Config): Try[ProfileLookup] = Try {
    ProfileLookup(
      profileName = config.getString("profile.name"),
      profileVersion = config.getString("profile.version"),
      profileBaseDir = new File(config.getString("profile.baseDir"))
    )
  }
  def toConfig(profileLookup: ProfileLookup): Config = {
    val config = Map(
      "profile.name" -> profileLookup.profileName,
      "profile.version" -> profileLookup.profileVersion,
      "profile.baseDir" -> profileLookup.profileBaseDir.getPath()
    ).asJava

    ConfigFactory.parseMap(config)
  }
}
