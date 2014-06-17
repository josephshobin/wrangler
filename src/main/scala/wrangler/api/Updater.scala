package wrangler.api

import scala.collection.JavaConverters._

import java.io.File

import scalaz._, Scalaz._

import com.typesafe.config.ConfigFactory

import org.eclipse.jgit.api.{Git => JGit}

import wrangler.data._
import wrangler.api.rest.RestError

sealed trait UnresolvedArtifact
case class Latest(group: String, name: String) extends UnresolvedArtifact
case class Specific(artifact: GenericArtifact) extends UnresolvedArtifact

sealed trait UpdaterError {
  def msg: String
}

case class UpdaterParseError(in: String) extends UpdaterError {
  val msg = s"Couldn't parse $in as artifact. Expected format is [group] % [name] % [specific-version|latest]"
}

case class UpdaterResolveError(latest: Latest, versions: List[Artifact]) extends UpdaterError {
  val msg = s"Couldn't find latest version for ${latest.group} % ${latest.name} in ${versions.map(_.pretty).mkString("\n")}"
}

case class UpdaterArtifactoryAuthenticationError(error: ArtifactoryAuthenticationError) extends UpdaterError {
  val msg = s"Failed to authenticate with artifactory and get latest versions. ${error.msg}"
}

case class UpdaterArtifactoryUnexpectedError(error: ArtifactoryUnexpectedError) extends UpdaterError {
  val msg = s"Unexpected failure getting latest versions from artifactory. ${error.msg}"
}

case class UpdaterArtifactoryParseError(error: ArtifactoryParseError) extends UpdaterError {
  val msg = s"Failed to parse artifacts from from artifactory. ${error.msg}"
}

case class UpdaterGitError(repo: String, error: Throwable) extends UpdaterError {
  val msg = s"Failed to clone $repo. $error"
}

case class UpdaterSbtError(repo: String, sbtError: SbtError) extends UpdaterError {
  val msg = s"Failed to update $repo. ${sbtError.msg}"
}

case class UpdaterRepoError(repo: String, error: RestError) extends UpdaterError {
  val msg = s"Failed to create pull request for $repo. ${error.msg}"
}

case class UpdaterShellError(repo: String, error: String) extends UpdaterError {
  val msg = s"Failed to run shell script on $repo. $error"
}

case class Config(targets: List[String], artifacts: List[UnresolvedArtifact])

object Automator {
  type Project = String

  def parseUpdaterConfig(path: String): UpdaterError \/ Config = {
    val config = ConfigFactory.parseFile(new File(path))
    val targets = config.getStringList("targets").asScala.toList
    val artifacts = config.getStringList("artifacts").asScala.toList.map(parseArtifact).sequenceU

    artifacts.map(as => Config(targets, as))
  }

  def parseArtifact(s: String): UpdaterError \/ UnresolvedArtifact = {
    val split = s.split("%").map(_.trim)

    if (split.length != 3) UpdaterParseError(s).left
    else if (split(2) == "latest") Latest(split(0), split(1)).right
    else Specific(GenericArtifact(split(0), split(1), split(2))).right
  }

  def resolveArtifact(artifact: UnresolvedArtifact, artifacts: List[Artifact]): UpdaterError \/ GenericArtifact = artifact match {
    case Specific(a)    => a.right
    case l@Latest(g, n) =>
      artifacts.find(a => a.group == g && a.name == n)
        .map(_.toGeneric.right)
        .getOrElse(UpdaterResolveError(l, artifacts).left)
  }

  def updateVersionsSbt(project: String, dir: File, artifacts: List[GenericArtifact]): Updater[SbtProject] = {
    SbtProject(dir).updateDependencies(artifacts)
      .flatMap(_.incrementVersion(Minor)) |> liftSbt(project)
  }

  def updateProject(
    gitUrl: String, project: String, targetBranch: String, branch: String,
    title: String, description: String, pullRequest: String => Repo[Unit], update: File => Updater[Unit]
  ): Updater[String] = {
    println(s"Updating $project")

    val dst = File.createTempFile("updater-", s"-$project")
    dst.delete

    val result = Git.clone(s"$gitUrl/$project", dst) |> liftGit(project) >>=
    (repo => Git.createBranch(repo, branch, targetBranch) |> liftGit(project)) >>=
    (repo => update(dst).map(_ => repo)) >>= { repo =>
      Git.add(".")(repo)
        .flatMap(Git.commit(s"$title\n\n$description"))
        .flatMap(Git.push(branch)) |> liftGit(project)
    } >>= (_ => pullRequest(project) |> liftRepo(project))

    dst.delete
    result.map(_ => s"Updated $project")
  }

  def runAutomator(
    gitUrl: String, repos: List[String], targetBranch: String, script: String, branch: String,
    title: String, description: String, pullReqest: String => Repo[Unit]
  ): List[Updater[String]] = {
    repos.map(repo => updateProject(
      gitUrl, repo, targetBranch, branch, title, description, pullReqest,
      dst => Util.run(List("bash", script), Some(dst)).map(_ => ()) |> liftShell(repo)
    ))
  }

  def runUpdater
    (configPath: String, artifacts: List[Artifact], gitUrl: String, createPullRequest: String => Repo[Unit])
      : Updater[List[Updater[String]]] = {
    parseUpdaterConfig(configPath) >>= { config =>
      config.artifacts.map(resolveArtifact(_, artifacts)).sequenceU.map { resolvedArtifacts =>
        config.targets.map(repoName => updateProject(
          gitUrl, repoName, "master", "wrangler/version_update", "Automatic version updater",
          s"""Updated:\n${resolvedArtifacts.map(_.pretty).mkString("\n")}""", createPullRequest,
          dst => updateVersionsSbt(repoName, dst, resolvedArtifacts).map(_ => ())
        ))
      }
    }
  }

  def liftArtifactory[T](result: Artifactory[T]): Updater[T] = result.leftMap {
    case e: ArtifactoryAuthenticationError => UpdaterArtifactoryAuthenticationError(e)
    case e: ArtifactoryUnexpectedError     => UpdaterArtifactoryUnexpectedError(e)
    case e: ArtifactoryParseError          => UpdaterArtifactoryParseError(e)
  }

  def liftSbt[T](project: String)(result: Sbt[T]): Updater[T]   = result.leftMap(UpdaterSbtError.apply(project, _))
  def liftGit[T](project: String)(result: Git[T]): Updater[T]   = result.leftMap(UpdaterGitError.apply(project, _))
  def liftRepo[T](project: String)(result: Repo[T]): Updater[T] = result.leftMap(UpdaterRepoError.apply(project, _))
  def liftShell[T](project: String)(result: String \/ T): Updater[T] = result.leftMap(UpdaterShellError.apply(project, _))
}
