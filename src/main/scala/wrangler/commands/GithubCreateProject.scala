package wrangler
package commands

import java.io.File

import scala.collection.JavaConverters._

import scalaz._, Scalaz._

import org.apache.felix.gogo.commands.{Argument => argument, Command => command, Option => option}
import org.apache.felix.service.command.CommandSession

import wrangler.api._

@command(scope = "omnia", name = "gh-create-project", description = "Creates a new project")
class GithubCreateProject extends FunctionalAction {
  @option(required = true, name = "--name", description = "Project name. Needs to be in the format category.name")
  var repo: String = null

  @option(required = true, name = "--user", description = "Github user name")
  var userIn: String = null

  @option(required = true, name = "--github-url", description = "Github url")
  var githubUrlIn: String = null

  @option(required = true, name = "--git-url", description = "Git url")
  var gitUrl: String = null

  @option(name = "--org", description = "Github organisation", required = true)
  var orgIn: String = null

  @option(required = true, name = "--artifactory-url", description = "Artifactory URL")
  var artifactoryUrl: String = null

  @option(required = true, name = "--artifactory-password", description = "Artifactory PASSWORD")
  var artifactoryPasswordIn: String = null

  @option(required = true, name = "--artifactory-repos", description = "Artifactory Repos")
  var artifactoryReposIn: String = null

  @option(required = true, name = "--g8-template", description = "G8 Template to use")
  var g8Template: String = null

  @option(required = true, name = "--omnia-ci-password", description = "Password used by travis to acces Github")
  var travisPassword: String = null

  @option(required = true, name = "--githubt-team-id", description = "Github team ID for the created repo")
  var teamId: Int = -1

  def execute(session: CommandSession): AnyRef = run(session) {
    implicit val s = session

    implicit val githubOrg     = Tag[String, GithubOrganisationT](orgIn)
    implicit val githubUrl     = Tag[String, GithubURLT](githubUrlIn)
    implicit val githubUser    = Tag[String, GithubUserT](userIn)
    implicit val artifactoryUser    = Tag[String, ArtifactoryUserT](userIn)
    implicit val artifactoryPassword = Tag[String, ArtifactoryPasswordT](artifactoryPasswordIn)

    val artifactoryRepos = artifactoryReposIn.split(",").toList

    val (result, pass) = Github.withAuthentication(p => Github.createRepo(repo, teamId)(githubOrg, githubUrl, githubUser, p))
    implicit val githubPassword = pass

    for {
      _         <- result.leftMap(_.toString)
      if (! new File(repo).exists)
      git       <- Git.clone(s"$gitUrl/$githubOrg/$repo.git", repo).leftMap(_.toString).leftMap(_.toString)
      _         <- Travis.createBuild(s"$githubOrg/$repo")
      _         <- Travis.addSecureVariables(repo, List("OMNIA_CI_PASSWORD" -> travisPassword, "ARTIFACTORY_PASSWORD" -> artifactoryPassword))
      artifacts <- artifactoryRepos.map(Artifactory.listLatest(artifactoryUrl, _)).sequenceU.map(_.flatten).leftMap(_.toString)
      _         <- Giter8.deployTemplate(
                     g8Template,
                     repo,
                     getVersions(artifacts)
                   )
      _         <- Util.run(Seq("chmod", "+x", s"$repo/sbt"))
      _         <- Git.add(git, ".").leftMap(_.toString)
      _         <- Git.commit(git, "Initial commit by Wrangler").leftMap(_.toString)
      _         <- Git.push(git, "master", "origin").leftMap(_.toString)
    } yield s"Successfully created $repo"
  }

  def getVersions(artifacts: List[Artifact]): Map[String, String] = {
    val dependencies = Map(
      "omnitool-core"          -> "omnitool_version",
      "tardis"                 -> "tardi_version",
      "omnia-test"             -> "omniatest_version",
      "uniform-core_2.10_0.13" -> "uniform_version"
    )

    artifacts
      .filter(a => a.group == "au.com.cba.omnia" && dependencies.keySet.contains(a.name))
      .flatMap(a => dependencies.get(a.name).map(_ -> a.version.pretty))
      .toMap
  }
}

