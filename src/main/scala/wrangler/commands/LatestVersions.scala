package wrangler
package commands

import scalaz._, Scalaz._

import com.quantifind.sumac.ArgMain

import wrangler.api.Artifactory
import wrangler.commands.args.MultipleArtifactoriesArgs

/** Arguments for `LatestVersions`.*/
class LatestVersionsArgs extends MultipleArtifactoriesArgs {}

/** Lists all the latest versions of all the specified repos in the specified Artifactories.*/
object LatestVersions extends ArgMain[LatestVersionsArgs] {
  /** Runs the command.*/
  def main(args: LatestVersionsArgs): Unit = {
    val artifacts =
      args.artifactories.map(a => Artifactory.listLatest(a.url, a.repos)(a.tuser, a.tpassword))
        .sequenceU
        .map(as => Artifactory.getLatest(as.flatten))

    println(artifacts.fold(_.msg, _.map(_.pretty).mkString("\n")))

    //Exit manually since dispatch hangs.
    sys.exit(0)
  }
}
