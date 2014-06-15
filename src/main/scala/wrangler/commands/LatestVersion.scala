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

@command(scope = "omnia", name = "list-versions", description = "List latest versions in Artifactory")
class LatestVersion extends FunctionalAction {
  @option(required = true, name = "--url", description = "Artifactory URL")
  var url: String = null

  @option(required = true, name = "--repo", description = "Artifactory Repository to search")
  var repo: String = null

  @option(required = true, name = "--user", description = "Artifactory user")
  var userIn: String = null

  @option(required = true, name = "--password", description = "Artifactory password")
  var passwordIn: String = null

  def execute(session: CommandSession): AnyRef = run(session) {
    implicit val user     = Tag[String, ArtifactoryUserT](userIn)
    implicit val password = Tag[String, ArtifactoryPasswordT](passwordIn)

    Artifactory.listLatest(url, repo)
      .map(_.map { case Artifact(g, a, v) => s"$g % $a % ${v.pretty}" }.sorted.mkString("\n"))
      .leftMap(_.toString)
  }
}
