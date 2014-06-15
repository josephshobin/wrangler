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

@command(scope = "omnia", name = "create-project", description = "Creates a new project")
class CreateProject extends FunctionalAction {
  @option(required = true, name = "--name", description = "Project name. Needs to be in the format category.name")
  var repo: String = null

  @option(required = true, name = "--user", description = "Stash user name")
  var userIn: String = null

  @option(required = true, name = "--stash-url", description = "Stash url")
  var stashUrlIn: String = null

  @option(required = true, name = "--git-url", description = "Git url")
  var gitUrl: String = null

  @option(required = true, name = "--teamcity-url", description = "Teamcity url")
  var teamcityUrlIn: String = null

  @option(required = true, name = "--teamcity-user", description = "Teamcity user name")
  var teamcityUserIn: String = null

  @option(required = true, name = "--artifactory-url", description = "Artifactory URL")
  var artifactoryUrl: String = null

  @option(required = true, name = "--artifactory-password", description = "Artifactory PASSWORD")
  var artifactoryPasswordIn: String = null

  @option(required = true, name = "--artifactory-repos", description = "Artifactory Repos")
  var artifactoryReposIn: String = null

  @option(required = true, name = "--project", description = "Base project")
  var projectIn: String = null

  @option(required = true, name = "--g8-template", description = "G8 Template to use")
  var g8Template: String = null

  @option(required = true, name = "--teamcity-template", description = "TeamCity template to use")
  var tcTemplate: String = null


  def execute(session: CommandSession): AnyRef = run(session) {
    implicit val s = session

    implicit val stashUrl     = Tag[String, StashURLT](stashUrlIn)
    implicit val stashUser    = Tag[String, StashUserT](userIn)
    implicit val stashProject = Tag[String, StashProjectT](projectIn)
    implicit val teamcityUrl     = Tag[String, TeamCityURLT](teamcityUrlIn)
    implicit val teamcityUser    = Tag[String, TeamCityUserT](teamcityUserIn)
    implicit val teamcityProject = Tag[String, TeamCityProjectT](projectIn)
    implicit val artifactoryUser    = Tag[String, ArtifactoryUserT](userIn)
    implicit val artifactoryPassword = Tag[String, ArtifactoryPasswordT](artifactoryPasswordIn)

    val Array(group, name) = repo.split("\\.")
    val artifactoryRepos = artifactoryReposIn.split(",").toList

    val (result, pass) = Stash.withAuthentication(p => Stash.createRepo(repo)(stashProject, stashUrl, stashUser, p))
    implicit val stashPassword = pass
    implicit val teamcityPassword = Tag[String, TeamCityPasswordT](stashPassword)

    for {
      _         <- result.leftMap(_.toString)
      if (! new File(repo).exists)
      _         <- Stash.fork(repo).leftMap(_.toString)
      _         <- Stash.forkSync(repo).leftMap(_.toString)
      git       <- Git.clone(s"$gitUrl/~$stashUser/$repo.git", name).leftMap(_.toString)
      _         <- Git.addRemote(git, "upstream", s"$gitUrl/$stashProject/$repo.git").leftMap(_.toString)
      _         <- TeamCity.createBuild(group, repo, tcTemplate).leftMap(_.toString)
      artifacts <- artifactoryRepos.map(Artifactory.listLatest(artifactoryUrl, _)).sequenceU.map(_.flatten).leftMap(_.toString)
      _         <- Giter8.deployTemplate(
                     g8Template,
                     name,
                     getVersions(artifacts) + ("group" -> group)
                   )
      _         <- Util.run(Seq("chmod", "+x", s"$name/sbt"))
      _         <- Git.add(git, ".").leftMap(_.toString)
      _         <- Git.commit(git, "Initial commit by Wrangler").leftMap(_.toString)
      _         <- Git.push(git, "master", "upstream").leftMap(_.toString)
      _         <- Util.run(Seq("mv", name, repo))
    } yield s"Successfully created $repo"

  }

  def getVersions(artifacts: List[Artifact]): Map[String, String] = {
    val dependencies = Map(
      "omnitool-core" -> "omnitool_version",
      "tardis"        -> "tardi_version",
      "omnia-test"    -> "omniatest_version",
      "uniform-core_2.10_0.13"  -> "uniform_version"
    )

    artifacts
      .filter(a => a.group == "au.com.cba.omnia" && dependencies.keySet.contains(a.name))
      .flatMap(a => dependencies.get(a.name).map(_ -> a.version.pretty))
      .toMap
  }
}

