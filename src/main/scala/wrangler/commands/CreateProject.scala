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

import scalaz._, Scalaz._

import com.quantifind.sumac.ArgMain
import com.quantifind.sumac.validation._

import wrangler.api._
import wrangler.data.Artifact
import wrangler.commands.args._

class CreateProjectArgs extends StashOrGithubArgs with MultiplArtifactoriesArgs {
  @Required
  var repo: String = _

  @Required
  var g8Template: String = _

  var travisGitPassword: String         = _
  var travisArtifactoryPassword: String = _

  var teamcityTemplate: String = _

  val teamcity = new TeamCityArgs {}

  addValidation {
    require(!useGithub || (useGithub && travisGitPassword != null), "Need to provided omnia-ci github password for travis to use")
    require(!useGithub || (useGithub && travisArtifactoryPassword != null), "Need to provided omnia-ci artifactory password for travis to use")
    require(!useStash || (useStash && teamcityTemplate != null), "Need to provided TeamCity template name to use for build")
  }
}

object CreateProject extends ArgMain[CreateProjectArgs] {
  case class Error(msg: String)

  type Result[T] = Error \/ T
  def liftRepo[T](result: Repo[T]): Result[T] = result.leftMap(e => Error(e.msg))
  def liftGit[T](result: Git[T]): Result[T] = result.leftMap(e => Error(e.toString))
  def liftArtifactory[T](result: Artifactory[T]): Result[T] = result.leftMap(e => Error(e.msg))
  def liftTeamcity[T](result: TeamCity[T]): Result[T] = result.leftMap(e => Error(e.msg))
  def liftOther[T](result: String \/ T): Result[T] = result.leftMap(e => Error(e))

  def main(args: CreateProjectArgs): Unit = {
    val repo = args.repo

    if (new File(repo).exists) {
      println(s"$repo already exists on local disk")
      sys.exit(1)
    }

    println("Retrieving latest versions")
    val artifacts =
      args.artifactories.map(a => Artifactory.listLatest(a.url, a.repos)(a.tuser, a.tpassword))
        .sequenceU
        .map(as => Artifactory.getLatest(as.flatten))


    
    liftArtifactory(artifacts).flatMap(as =>
      if (args.useGithub) setupGithub(repo, args, as)
      else setupStash(repo, args, as)
    ).fold(
      e => {
        println(e.msg)
        sys.exit(1)
      },
      identity
    )
  }

  def setupGithub(repo: String, args: CreateProjectArgs, artifacts: List[Artifact]): Result[String] = {
      val github = args.ogithub.get
      implicit val apiUrl = github.tapiUrl
      implicit val user   = github.tuser
      implicit val org    = github.torg

      val gitUrl = s"${github.gitUrl}/$org/$repo"

      println(s"Creating Github repo $repo")
      val (initial, pass) = Github.retryUnauthorized(github.tpassword, p => Github.createRepo(repo, github.teamid)(org, apiUrl, user, p))
      implicit val password = pass

      for {
        _   <- initial |> liftRepo
        _    = println(s"Cloning $repo")
        git <- Git.clone(gitUrl, repo) |> liftGit
        _    = println("Applying template")
        _   <- Giter8.deployTemplate(
                 args.g8Template,
                 repo,
                 getVersions(artifacts)
               ) |> liftOther
        _    = println("Setting up travis")
        _   <- Travis.createBuild(s"$org/$repo") |> liftOther
        _   <- Travis.addSecureVariables(repo, List("OMNIA_CI_PASSWORD" -> args.travisGitPassword, "ARTIFACTORY_PASSWORD" -> args.travisArtifactoryPassword)) |> liftOther
        _    = println("Pushing changes")
        _   <- Git.add(".")(git) |> liftGit
        _   <- Git.commit("Initial commit by Wrangler")(git) |> liftGit
        _   <- Git.push("master", "origin")(git) |> liftGit
      } yield s"Created project $repo"
  }

  def setupStash(repo: String, args: CreateProjectArgs, artifacts: List[Artifact]): Result[String] = {
    val stash = args.ostash.get
    implicit val apiUrl  = stash.tapiUrl
    implicit val user    = stash.tuser
    implicit val project = stash.tproject

    val teamcity = args.teamcity
    implicit val tcUrl     = teamcity.turl
    implicit val tcProject = teamcity.tproject
    implicit val tcUser    = teamcity.tuser

    val gitUrl = stash.gitUrl

    val Array(group, name) = repo.split("\\.")

    println(s"Creating Stash repo $repo")
    val (initial, pass) = Stash.retryUnauthorized(stash.tpassword, p => Stash.createRepo(repo)(project, apiUrl, user, p))
    implicit val password = pass
    implicit val tcPassword = Tag[String, TeamCityPasswordT](password)

    val tmp = File.createTempFile("wrangler-", s"-$repo")
    tmp.delete
    tmp.mkdir
    tmp.deleteOnExit
    val dst = s"$tmp/$name"

    for {
      _   <- initial |> liftRepo
      _   <- Stash.fork(repo) |> liftRepo
      _   <- Stash.forkSync(repo) |> liftRepo
      _    = println("Cloning repo")
      git <- Git.clone(s"$gitUrl/~$user/$repo.git", dst) |> liftGit
      _   <- Git.addRemote(git, "upstream", s"$gitUrl/$project/$repo.git") |> liftGit
      _    = println("Applying template")
      _   <- Giter8.deployTemplate(
               args.g8Template,
               name,
               getVersions(artifacts) + ("group" -> group),
               Some(tmp)
             ) |> liftOther
      _    = println("Pushing changes")
      _   <- Git.add(".")(git) |> liftGit
      _   <- Git.commit("Initial commit by Wrangler")(git) |> liftGit
      _   <- Git.push("master", "upstream")(git) |> liftGit
      _   <- Util.run(Seq("mv", dst, repo)) |> liftOther
      _    = println("Creating TeamCity build")
      _   <- TeamCity.createBuild(group, repo, args.teamcityTemplate) |> liftTeamcity
    } yield s"Created $repo"
  }

  def getVersions(artifacts: List[Artifact]): Map[String, String] = {
    val dependencies = Map(
      //"omnitool-core"          -> "omnitool_version",
      //"tardis"                 -> "tardis_version",
      //"omnia-test"             -> "omniatest_version",
      "uniform-core_2.10_0.13" -> "uniform_version"
    )

    artifacts
      .filter(a => a.group == "au.com.cba.omnia" && dependencies.keySet.contains(a.name))
      .flatMap(a => dependencies.get(a.name).map(_ -> a.version.pretty))
      .toMap
  }
}
