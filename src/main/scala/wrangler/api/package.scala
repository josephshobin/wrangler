package wrangler

import scalaz._, Scalaz._

package object api {
  type Artifactory[T] = ArtifactoryError \/ T
  type Sbt[T]         = SbtError \/ T
  type Git[T]         = Throwable \/ T
  type Updater[T]     = UpdaterError \/ T

  type Rest[T]     = wrangler.api.rest.RestError \/ T
  type Repo[T]     = wrangler.api.rest.RestError \/ T
  type TeamCity[T] = wrangler.api.rest.RestError \/ T

  type ParsedVersion[T] = wrangler.data.VersionParseError \/ T
}


