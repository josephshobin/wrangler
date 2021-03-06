//   Copyright 2014 Commonwealth Bank of Australia
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

package wrangler
package commands

import scala.Console

import scalaz._, Scalaz._

import com.quantifind.sumac.ArgMain
import com.quantifind.sumac.validation._

import wrangler.commands.args._
import wrangler.api.{Automator => AAutomator, _}

/** Arguments for `Automator`.*/
class AutomatorArgs extends StashOrGithubArgs {
  @Required
  var repos: List[String] = _ // use '-' to read from stdin
  @Required
  var branch: String = _
  @Required
  var title: String = _
  @Required
  var description: String = _
  @Required
  var script: String = _

  var targetBranch: String = "master"
  var dryRun: Boolean      = false
}

/** 
  * Runs a shell script against all the specified repos and creates pull request with the changes.
  * 
  * It does that by:
  *  1. Cloning the repo to a temporary location.
  *  1. Creating and checking out a new branch.
  *  1. Running the shell script with the repo as working directory.
  *  1. Commits and pushes the changes.
  *  1. Creates a pull request against master.
  */
object Automator extends ArgMain[AutomatorArgs] {
  /** Runs the command.*/
  def main(args: AutomatorArgs): Unit = {
    def createPullRequest(repo: String): Repo[Unit] =
      if (args.dryRun) {
        println(s"Skipping $repo")
        ().right
      } else if (args.useGithub) {
        val gh = args.ogithub.get

        val (initial, pass) = Github.retryUnauthorized(
          gh.tpassword,
          p => Github.listRepos(gh.org)(gh.tapiUrl, gh.tuser, p)
        )

        Github.pullRequest(
          repo, args.branch, args.targetBranch, args.title, args.description
        )(gh.torg, gh.tapiUrl, gh.tuser, pass).map(_ => ())

      } else {
        val stash = args.ostash.get

        val (initial, pass) = Stash.retryUnauthorized(
          stash.tpassword,
          p => Stash.listRepos(stash.project)(stash.tapiUrl, stash.tuser, p)
        )

        Stash.pullRequest(
          repo, args.branch, args.targetBranch, args.title, args.description, stash.treviewers
        )(stash.tproject, stash.tapiUrl, stash.tuser, stash.tpassword).map(_ => ())
      }

    val gitUrl =
      if (args.useGithub) s"${args.ogithub.get.gitUrl}/${args.ogithub.get.org}"
      else s"${args.ostash.get.gitUrl}/${args.ostash.get.project}"

    val repos = 
      if (args.repos != List("-")) args.repos
      else Iterator.continually(Console.readLine).takeWhile(_ != null)

    val result = AAutomator.runAutomator(
      gitUrl, args.repos, args.targetBranch, args.script,
      args.branch, args.title, args.description, createPullRequest
    )

    val successes = result.flatMap(_.toOption).mkString("\n")
    val failures  = result.flatMap(_.swap.toOption.map(_.msg)).mkString("\n")
    val formatted = s"$successes\nErrors:\n$failures"

    println(formatted)

    //Exit manually since dispatch hangs.
    sys.exit(0)
  }
}
