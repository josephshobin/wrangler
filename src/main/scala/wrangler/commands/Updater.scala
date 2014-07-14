package wrangler.commands

import scalaz._, Scalaz._

import com.quantifind.sumac.ArgMain
import com.quantifind.sumac.validation._

import wrangler.api.{Automator => AAutomator, _}
import wrangler.commands.args._
import wrangler.data._

/** Arguments for `Updater`.*/
class UpdaterArgs extends WranglerArgs with StashOrGithubArgs with MultipleArtifactoriesArgs {
  @Required
  var updaterConfig: String = _
}

/**
  * Updates the versions of the dependencies for the sbt projects on Github or Stash.
  *
  * It does that by:
  *  1. Parsing the updater config to extract the artifact versions and repos to update.
  *  1. Getting the latest list of artifacts from the specified Artifactories.
  *  1. Resolving the artifacts from the config with version `latest` against the latest versions
  *     from artifactory.
  *  1. Clonging each repo.
  *  1. Updating the versions.
  *  1. Bumping the minor version.
  *  1. Creating a pull request with the changes.
  */
object Updater extends ArgMain[UpdaterArgs] {
  /** Runs the command.*/
  def main(args: UpdaterArgs): Unit = {
    def createPullRequest(repo: String): Repo[Unit] =
      if (args.useGithub) {
        val gh = args.ogithub.get

        val (initial, pass) = Github.retryUnauthorized(
          gh.tpassword,
          p => Github.listRepos(gh.org)(gh.tapiUrl, gh.tuser, p)
        )

        Github.pullRequest(
          repo, "wrangler/version_update", "master", "Automatic version update", ""
        )(gh.torg, gh.tapiUrl, gh.tuser, pass).map(_ => ())

      } else {
        val stash = args.ostash.get

        val (initial, pass) = Stash.retryUnauthorized(
          stash.tpassword,
          p => Stash.listRepos(stash.project)(stash.tapiUrl, stash.tuser, p)
        )

        Stash.pullRequest(
          repo, "wrangler/version_update", "master", "Automatic version update", "", stash.treviewers
        )(stash.tproject, stash.tapiUrl, stash.tuser, pass).map(_ => ())
      }

    val gitUrl = 
      if (args.useGithub) s"${args.ogithub.get.gitUrl}/${args.ogithub.get.org}"
      else s"${args.ostash.get.gitUrl}/${args.ostash.get.project}"

    println(args.artifactory.repos)

    val artifacts =
      args.artifactories.map(a => Artifactory.listLatest(a.url, a.repos)(a.tuser, a.tpassword))
        .sequenceU
        .map(as => Artifactory.getLatest(as.flatten))

    val result =
      AAutomator.liftArtifactory(artifacts) >>= { as =>
        AAutomator.runUpdater(args.updaterConfig, as, gitUrl, createPullRequest)
      }

    val formatted = result.fold(
      e => s"Failed to run Updater. ${e.msg}",
      rs => {
        val successes = rs.flatMap(_.toOption).mkString("\n")
        val failures  = rs.flatMap(_.swap.toOption.map(_.msg)).mkString("\n")

        s"$successes\nErrors:\n$failures"
      }
    )

    println(formatted)

    //Exit manually since dispatch hangs.
    sys.exit(0)
  }
}
