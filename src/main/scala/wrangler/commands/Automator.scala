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

import java.io.File

import scala.collection.JavaConverters._

import scalaz._, Scalaz._

import org.apache.felix.gogo.commands.{Argument => argument, Command => command, Option => option}
import org.apache.felix.service.command.CommandSession

import wrangler.api._

@command(scope = "omnia", name = "automator", description = "Given a shell script and a list of repos runs the script against each repo and creates pull requests")
class Automator extends FunctionalAction {
  @option(required = true, name = "--user", description = "Stash user name")
  var userIn: String = null

  @option(required = true, name = "--stash-url", description = "Stash url")
  var stashUrlIn: String = null

  @option(required = true, name = "--git-url", description = "Git url")
  var gitUrl: String = null

  @option(required = true, name = "--project", description = "Stash base project")
  var projectIn: String = null

  @option(required = true, name = "--repos", description = "Comma separate list of repos to action")
  var reposIn: String = null
  lazy val repos = reposIn.split(",").toList

  @option(required = true, name = "--script", description = "Shell script to execute in each repo")
  var script: String = null

  @option(required = true, name = "--branch", description = "Branch to do the work in")
  var branch: String = null

  @option(required = true, name = "--pr-title", description = "Pull request title")
  var title: String = null

  @option(required = true, name = "--pr-description", description = "Pull request description")
  var description: String = null

  def execute(session: CommandSession): AnyRef = run(session) {
    implicit val s = session

    implicit val url     = Tag[String, StashURLT](stashUrlIn)
    implicit val user    = Tag[String, StashUserT](userIn)
    implicit val project = Tag[String, StashProjectT](projectIn)

    val (result, pass) = Stash.withAuthentication(p => Stash.listRepos(project)(url, user, p))
    implicit val password = pass
    result.leftMap(_.toString).map{ _ =>
      Util.automator(repos, script, s"$gitUrl/$project", s"automator/$branch", title, description)
        .map(_.fold(identity, identity))
        .mkString("\n")
    }
  }
}

