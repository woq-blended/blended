package blended.updater.tools.configbuilder

import blended.updater.config.FragmentConfig
import blended.updater.config.RuntimeConfig
import scala.util.Try
import java.io.File
import com.typesafe.config.ConfigFactory

trait FragmentResolver {
  import FragmentResolver._

  def resolveFragments(runtimeConfig: RuntimeConfig, fragments: Seq[FragmentConfig]): Either[Seq[Fragment], Seq[FragmentConfig]] = {

    val requested: Seq[FragmentRef] = runtimeConfig.fragments.map(f => FragmentRef(f.name, f.version, f.url))

    var resolved: Map[FragmentRef, FragmentConfig] = Map()
    var unresolved: Map[FragmentRef, Seq[FragmentRef]] = Map()
    var unseen: Seq[FragmentConfig] = Seq()

    var cont = true

    while (cont) {

    }

    ???
  }

}

object FragmentResolver {

  case class FragmentRef(name: String, version: String, url: Option[String])

  class ResolveContext(runtimeConfig: RuntimeConfig, fragments: Seq[FragmentConfig]) {

    private[this] var cache: Map[FragmentRef, FragmentConfig] = Map()

    fragments.flatMap(flattenFragments).filter(isResolved).foreach { f =>
      cache += FragmentRef(f.name, f.version, f.url) -> f
    }

    def fetchFragment(fragment: FragmentRef): Option[FragmentConfig] = cache.get(fragment).orElse {
      fragment.url match {
        case None => None
        case Some(unresolveUrl) =>
          Try {
            val url = runtimeConfig.resolveBundleUrl(unresolveUrl).get
            val file = File.createTempFile(runtimeConfig.resolveFileName(url).get, "")
            RuntimeConfig.download(url, file).get
            val config = ConfigFactory.parseFile(file).resolve()
            file.delete()
            FragmentConfig.read(config).get
          }.toOption.map { fetched =>
            synchronized {
              cache += fragment -> fetched
              fetched
            }
          }
      }
    }
  }

  sealed trait Fragment
  final case class Unresolved(fragmentRef: FragmentRef) extends Fragment
  final case class Resolved(fragment: FragmentConfig) extends Fragment {
    def fragementRef = FragmentRef(fragment.name, fragment.version, fragment.url)
  }

  def isResolved(fragment: FragmentConfig): Boolean =
    (!fragment.bundles.isEmpty || !fragment.fragments.isEmpty || fragment.url.isEmpty)

  def flattenFragments(fragment: FragmentConfig): Seq[FragmentConfig] =
    fragment +: fragment.fragments.flatMap(flattenFragments)

  def findUnresolved(fragment: FragmentConfig): Seq[FragmentConfig] =
    flattenFragments(fragment).filterNot(isResolved)

  def resolve(fragment: FragmentConfig, context: ResolveContext): Try[FragmentConfig] = Try {
    if (isResolved(fragment))
      fragment
    else {
      context.fetchFragment(FragmentRef(fragment.name, fragment.version, fragment.url)) match {
        case Some(fetchedFragment) =>
          fetchedFragment.copy(fragments = fetchedFragment.fragments.map(f => resolve(f, context).get))
          fetchedFragment
        case None => sys.error("Could not resolve fragment: " + fragment)
      }
    }
  }

  def resolve(runtimeConfig: RuntimeConfig, fragments: Seq[FragmentConfig]): Try[RuntimeConfig] = Try {
    val context = new ResolveContext(runtimeConfig, fragments)
    runtimeConfig.copy(
      fragments = runtimeConfig.fragments.map(f => resolve(f, context).get)
    )
  }
}