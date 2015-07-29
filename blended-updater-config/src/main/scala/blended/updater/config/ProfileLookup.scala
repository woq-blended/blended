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
    val profileName = config.getString("profile.name")
    val profileVersion = config.getString("profile.version")
    val profileBaseDir = new File(config.getString("profile.baseDir"))

    ProfileLookup(
      profileName = profileName,
      profileVersion = profileVersion,
      profileBaseDir = profileBaseDir
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
