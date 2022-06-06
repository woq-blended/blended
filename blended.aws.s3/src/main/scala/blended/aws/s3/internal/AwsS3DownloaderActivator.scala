package blended.aws.s3.internal

import blended.aws.s3.{AwsS3Downloader, AwsS3Config}
import blended.domino.TypesafeConfigWatching
import domino.DominoActivator

class AwsS3DownloaderActivator extends DominoActivator with TypesafeConfigWatching  {

  whenBundleActive{
    whenTypesafeConfigAvailable { case (cfg, ctxt) =>

      val cfg = AwsS3Config.default
      AwsS3DownloaderImpl.make(cfg).providesService[AwsS3Downloader]
    }
  }
}
