package blended.updater.config

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters._
import scala.util.Try

/**
 * Information used by the Blended Launcher and Blended Updater to determine or persist
 * the currently selected and active profile configuration.
 *
 * @param profileName The name of the profile.
 * @param profileVersion The version of the profile.
 * @param profileBaseDir The directory, where the files of the profile will be looked up.
 *   The determine the concrete profile directory, use [[ProfileLookup#materializedDir]]
 *
 * @see [[blended.launcher.Launcher]]
 * @see [[blended.updater.Updater]]
 */
case class ProfileLookup(
  profileName : String,
  profileVersion : String,
  profileBaseDir : File
) {

  override def toString() : String = s"${getClass.getSimpleName}(profileName=${profileName}, profileVersion=${profileVersion},profileBaseDir=${profileBaseDir})"

  /**
   * The directory where the profile including it's overlays is installed.
   * It is always a sub directory of the `profileBaseDir`.
   *
   * @see [[OverlayConfigCompanion#materializedDir]]
   */
  def materializedDir : File = {
    val pureProfileDir = new File(profileBaseDir, s"${profileName}/${profileVersion}")
    // LocalOverlays.materializedDir(overlays, pureProfileDir)
    pureProfileDir
  }

}

object ProfileLookup {

  /**
   * Try to read a [[ProfileLookup]] from a [[Config]].
   */
  def read(config : Config) : Try[ProfileLookup] = Try {
    val profileName = config.getString("profile.name")
    val profileVersion = config.getString("profile.version")
    val profileBaseDir = new File(config.getString("profile.baseDir"))

    ProfileLookup(
      profileName = profileName,
      profileVersion = profileVersion,
      profileBaseDir = profileBaseDir
    )
  }

  /**
   * Create a [[Config]] from a [[ProfileLookup]].
   */
  def toConfig(profileLookup : ProfileLookup) : Config = {
    val config = Map[String, Object](
      "profile.name" -> profileLookup.profileName,
      "profile.version" -> profileLookup.profileVersion,
      "profile.baseDir" -> profileLookup.profileBaseDir.getPath()
    ).asJava

    ConfigFactory.parseMap(config)
  }
}
