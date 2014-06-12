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

@command(scope = "omnia", name = "list-repos", description = "List stash repos")
class ListRepos extends FunctionalAction {
  @option(required = true, name = "--user", description = "Stash user name")
  var userIn: String = null

  @option(name = "--stash-url", description = "Stash url", required = true)
  var stashUrlIn: String = null

  @option(name = "--project", description = "Stash base project", required = true)
  var project: String = null


  def execute(session: CommandSession): AnyRef = run(session) {
    implicit val s = session

    implicit val url     = Tag[String, StashURLT](stashUrlIn)
    implicit val user    = Tag[String, StashUserT](userIn)

    val (result, _) = Stash.withAuthentication(p => Stash.listRepos(project)(url, user, p))

    result.map(_.mkString("\n")).leftMap(_.toString)
  }
}

