package blended.mgmt.mock

import java.text.DecimalFormat
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import blended.updater.config._

import scala.util.Random

object MockObjects {

  private[this] lazy val countries : List[String] = List("de", "cz", "bg", "ro")
  private[this] lazy val connectIds : List[String] = List("A", "B")

  private[this] lazy val serviceCount = new AtomicInteger(0)
  private[this] lazy val containerCount = new AtomicInteger(0)

  private[this] lazy val rnd = new Random()

  private[this] def pickOne[T](l : List[T]) : T = l(rnd.nextInt(l.size))

  private[this] def containerProps(ctNum : Integer) = Map(
    "sib.country" -> pickOne(countries),
    "sib.location" -> new DecimalFormat("00000").format(ctNum),
    "sib.connectId" -> pickOne(connectIds)
  )

  private[this] def sizedProperties(namePrefix : String = "prop", numProps : Int) =
    1.to(numProps).map(i => (s"$namePrefix-$i", s"value$i")).toMap

  // scalastyle:off magic.number
  private[this] def serviceInfo(numProps : Int = 10) : ServiceInfo = ServiceInfo(
    name = s"service-${serviceCount.incrementAndGet()}",
    serviceType = s"type-${rnd.nextInt(3) + 1}",
    timestampMsec = System.currentTimeMillis(),
    lifetimeMsec = 5000L,
    props = sizedProperties(namePrefix = "property", numProps = rnd.nextInt(10) + 1)
  )
  // scalastyle:on magic.number

  private[this] def serviceSeq(numServices : Int) : List[ServiceInfo] =
    1.to(numServices).map(_ => serviceInfo()).toList

  val noOverlays = OverlaySet(
    overlays = Set.empty[OverlayRef],
    state = OverlayState.Valid,
    reason = None
  )

  val someOverlays = OverlaySet(
    overlays = Set(
      OverlayRef(name = "java-medium", version = "1.0"),
      OverlayRef(name = "shop-A", version = "1.0")
    ),
    state = OverlayState.Active,
    reason = None
  )

  val invalid = OverlaySet(
    overlays = Set(
      OverlayRef(name = "java-small", version = "1.0"),
      OverlayRef(name = "shop-Q", version = "1.0")
    ),
    state = OverlayState.Invalid,
    reason = Some("Incorrect artifact checksums")
  )

  lazy val validProfiles : List[Profile] = List(
    ProfileGroup(name = "blended-demo", "1.0", List(noOverlays)),
    ProfileGroup(name = "blended-simple", "1.0", List(noOverlays, someOverlays, invalid)),
    ProfileGroup(name = "blended-simple", "1.1", List(noOverlays, someOverlays, invalid))
  ).flatMap(_.toSingle)

  def createContainer(numContainers : Integer) : List[ContainerInfo] = 1.to(numContainers).map { _ =>

    // scalastyle:off magic.number
    val serviceSeqs = 1.to(3).map { _ =>
      serviceSeq(rnd.nextInt(5) + 1)
    }.toList
    // scalastyle:on magic.number

    ContainerInfo(
      containerId = UUID.randomUUID().toString(),
      properties = containerProps(containerCount.incrementAndGet()),
      serviceInfos = pickOne(serviceSeqs),
      profiles = validProfiles,
      timestampMsec = System.currentTimeMillis(),
      appliedUpdateActionIds = Nil
    )
  }.toList
}

