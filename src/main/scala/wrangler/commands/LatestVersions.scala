package wrangler
package commands

import scalaz._, Scalaz._

import com.quantifind.sumac.ArgMain

import wrangler.api.Artifactory
import wrangler.commands.args.MultiplArtifactoriesArgs

class LatestVersionsArgs extends MultiplArtifactoriesArgs {}

object LatestVersions extends ArgMain[LatestVersionsArgs] {
  def main(args: LatestVersionsArgs): Unit = {
    val artifacts =
      args.artifactories.map(a => Artifactory.listLatest(a.url, a.repos)(a.tuser, a.tpassword))
        .sequenceU
        .map(as => Artifactory.getLatest(as.flatten))

    println(artifacts.fold(_.msg, _.map(_.pretty).mkString("\n")))
  }
}
