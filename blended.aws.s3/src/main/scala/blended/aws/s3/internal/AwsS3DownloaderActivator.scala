package blended.aws.s3.internal

import blended.domino.TypesafeConfigWatching
import domino.DominoActivator

class AwsS3DownloaderActivator extends DominoActivator with TypesafeConfigWatching  {

  whenBundleActive{
    whenTypesafeConfigAvailable { case (_, _) =>

    }
  }
}
