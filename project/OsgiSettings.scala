import com.typesafe.sbt.osgi.SbtOsgi
import phoenix.ProjectConfig
import sbt.AutoPlugin

trait OsgiSettings extends ProjectConfig {

  /** If `true` (default), this project is packaged as OSGi bundle. */
  def osgi: Boolean = true

  /**
    * The Bundle configuration. The Bundle ID is the [[projectName]]
    */
  def bundle: BlendedBundle = BlendedBundle(
    bundleSymbolicName = projectName,
    exportPackage = Seq(projectName),
    privatePackage = Seq(s"${projectName}.internal.*")
  )

  override def settings: Seq[sbt.Setting[_]] = super.settings ++ {
    if (osgi) bundle.osgiSettings else Seq()
  }

  override def plugins: Seq[AutoPlugin] = super.plugins ++ {
    if (osgi) Seq(SbtOsgi) else Seq()
  }

}
