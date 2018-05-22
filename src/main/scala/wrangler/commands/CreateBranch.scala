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

package wrangler.commands

import java.io.File

import scala.Console
import scalaz._, Scalaz._

import com.quantifind.sumac.ArgMain
import com.quantifind.sumac.validation._

import wrangler.api.Automator.{liftGit, liftShell, updateProject, gitBranch}
import wrangler.commands.args._

import wrangler.api.{Automator => AAutomator, _}

/** Arguments for `Automator`.*/
class CreateBranchArgs extends StashOrGithubArgs {
  @Required
  var repos: List[String] = _ // use '-' to read from stdin

  var branch: Option[String]       = None
  var title: Option[String]        = None
  var description: Option[String]  = None
  var targetBranch: Option[String] = None
}

/** 
  * Creates a branch in the specified repos.
  * 
  * It does that by:
  *  1. Cloning the repo to a temporary location.
  *  1. Creating and checking out a new branch.
  */
object CreateBranch extends ArgMain[CreateBranchArgs] {
  /** Runs the command.*/
  def main(args: CreateBranchArgs): Unit = {

    val branch =       args.branch.getOrElse("wrangler/branch_creation")
    val targetBranch = args.targetBranch.getOrElse("master")
    val title =        args.title.getOrElse("Automatic branch creation")
    val description =  args.title.getOrElse("")

    val gitUrl =
      if (args.useGithub) s"${args.ogithub.get.gitUrl}/${args.ogithub.get.org}"
      else s"${args.ostash.get.gitUrl}/${args.ostash.get.project}"

    val repos = 
      if (args.repos != List("-")) args.repos
      else Iterator.continually(Console.readLine).takeWhile(_ != null)

    val result = args.repos.map(repo => gitBranch(
      gitUrl, repo, targetBranch, branch, title, description
    ))

    val successes = result.flatMap(_.toOption).mkString("\n")
    val failures  = result.flatMap(_.swap.toOption.map(_.msg)).mkString("\n")
    val formatted = s"$successes\nErrors:\n$failures"

    println(formatted)

    //Exit manually since dispatch hangs.
    sys.exit(0)
  }
}
