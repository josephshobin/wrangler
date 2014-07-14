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

case class UpdaterParseError(in: String)
    extends UpdaterError {
  val msg =
    s"Couldn't parse $in as artifact. Expected format is [group] % [name] % [specific-version|latest]"
}

case class UpdaterResolveError(latest: Latest, versions: List[Artifact])
    extends UpdaterError {
  val msg =
    s"Couldn't find latest version for ${latest.group} % ${latest.name} in" ++
    s"${versions.map(_.pretty).mkString("\n")}"
}

case class UpdaterArtifactoryAuthenticationError(error: ArtifactoryAuthenticationError)
    extends UpdaterError {
  val msg = s"Failed to authenticate with artifactory and get latest versions. ${error.msg}"
}

case class UpdaterArtifactoryUnexpectedError(error: ArtifactoryUnexpectedError)
    extends UpdaterError {
  val msg = s"Unexpected failure getting latest versions from artifactory. ${error.msg}"
}

case class UpdaterArtifactoryParseError(error: ArtifactoryParseError)
    extends UpdaterError {
  val msg = s"Failed to parse artifacts from from artifactory. ${error.msg}"
}

case class UpdaterGitError(repo: String, error: Throwable)
    extends UpdaterError {
  val msg = s"Failed to clone $repo. $error"
}

case class UpdaterSbtError(repo: String, sbtError: SbtError)
    extends UpdaterError {
  val msg = s"Failed to update $repo. ${sbtError.msg}"
}

case class UpdaterRepoError(repo: String, error: RestError)
    extends UpdaterError {
  val msg = s"Failed to create pull request for $repo. ${error.msg}"
}

case class UpdaterShellError(repo: String, error: String)
    extends UpdaterError {
  val msg = s"Failed to run shell script on $repo. $error"
}

/**
  * Config for `Automator.runUpdater` which has the list of repos to update
  * and the artifacts to update.
  */
case class Config(targets: List[String], artifacts: List[UnresolvedArtifact])

/** 
  * Automator can make changes to a list of git repos, such as applying a shell script to each repo
  * or updating a versions of artifacts, commit those changes and raise pull requests for each of
  * the repos.
  * 
  * Starting points are `runAutomator` to apply a shell script or `runUpdater` to update versions.
  */
object Automator {
  type Project = String

  /**
    * Parse the config for the version updater.
    * 
    * It needs to specify a list of repos to update and a list of artifacts whose versions it should
    * change. Instead of a target version the version could be latest. This can then be resolved to
    * a specific version number in `resolveArtifact`.
    * 
    * An example is:
    * {{{
    * targets = [wrangler, maestro]
    * artifacts = [
    *   au.com.cba.omnia % edge   % 2.1.0-20140604032756-0c0abb1,
    *   au.com.cba.omnia % tardis % latest,
    *   org.apache.sqoop % sqoop  % 1.4.3-cdh4.6.0
    * ]
    * }}}
    */
  def parseUpdaterConfig(path: String): Updater[Config] = {
    val config = ConfigFactory.parseFile(new File(path))
    val targets = config.getStringList("targets").asScala.toList
    val artifacts = config.getStringList("artifacts").asScala.toList.map(parseArtifact).sequenceU

    artifacts.map(as => Config(targets, as))
  }

  /** Parse an individual artifact string in the config.*/
  def parseArtifact(s: String): Updater[UnresolvedArtifact] = {
    val split = s.split("%").map(_.trim)

    if (split.length != 3) UpdaterParseError(s).left
    else if (split(2) == "latest") Latest(split(0), split(1)).right
    else Specific(GenericArtifact(split(0), split(1), split(2))).right
  }

  /** 
    * Resolves the artifact with the version of latest against a
    * list of provided artifacts with versions.
    */
  def resolveArtifact(artifact: UnresolvedArtifact, artifacts: List[Artifact]): Updater[GenericArtifact] = artifact match {
    case Specific(a)    => a.right
    case l@Latest(g, n) =>
      artifacts.find(a => a.group == g && a.name == n)
        .map(_.toGeneric.right)
        .getOrElse(UpdaterResolveError(l, artifacts).left)
  }

  /** Update all the versions of the specified artifacts in an sbt project and bump the minor version.*/
  def updateVersionsSbt(project: String, dir: File, artifacts: List[GenericArtifact]): Updater[SbtProject] = {
    SbtProject(dir).updateDependencies(artifacts)
      .flatMap(_.incrementVersion(Minor)) |> liftSbt(project)
  }

  /**
    * Changes the specified git repo.
    *
    * It does that by:
    *  1. Cloning the project to a tmp location.
    *  1. Creating a new branch based on the specified existing branch.
    *  1. Applying `update` to the repo.
    *  1. Committing and pushing the changes.
    *  1. Rasing a pull request using `pullRequest`.
    */
  def updateProject(
    gitUrl: String, project: String, targetBranch: String, branch: String,
    title: String, description: String, pullRequest: String => Repo[Unit], update: File => Updater[Unit]
  ): Updater[String] = {
    println(s"Updating $project")

    val dst = File.createTempFile("updater-", s"-$project")
    dst.delete

    val result = Git.clone(s"$gitUrl/$project", dst) |> liftGit(project) >>=
    (repo => Git.createBranch(branch, targetBranch)(repo) |> liftGit(project)) >>=
    (repo => update(dst).map(_ => repo)) >>= { repo =>
      Git.add(".")(repo)
        .flatMap(Git.commit(s"$title\n\n$description"))
        .flatMap(Git.push(branch)) |> liftGit(project)
    } >>= (_ => pullRequest(project) |> liftRepo(project))

    dst.delete
    result.map(_ => s"Updated $project")
  }

  /**
    * Applies the specified shell script to the specified repos and creates pull requests with the
    * changes using the specified `pullRequest` function.
    */
  def runAutomator(
    gitUrl: String, repos: List[String], targetBranch: String, script: String, branch: String,
    title: String, description: String, pullReqest: String => Repo[Unit]
  ): List[Updater[String]] = {
    repos.map(repo => updateProject(
      gitUrl, repo, targetBranch, branch, title, description, pullReqest,
      dst => Util.run(List("bash", script), Some(dst)).map(_ => ()) |> liftShell(repo)
    ))
  }

  /**
   * Copy files from the fully qualified directory given by fromDir to the relative directory
   * in the repo given by destRepoDir.
   */
  def copyFilesToRepo(fromDir: String, destRepoDir: String, repoDir: File) = {
    val destDir =  repoDir.getPath() + "/" + destRepoDir
    Util.run(List("cp", "-r", fromDir, destDir))
  }

  /**
   * Adds the files in the given directory to the specified repo and creates a pull request
   * with the changes using the specified `pullRequest` function.
   */
  def runAddFiles(
    gitUrl: String, repos: List[String], targetBranch: String, sourceDir: String, destRepoDir: String,
    branch: String, title: String, description: String, pullReqest: String => Repo[Unit]
  ): List[Updater[String]] = {
    repos.map(repo => updateProject(
      gitUrl, repo, targetBranch, branch, title, description, pullReqest,
      dst => copyFilesToRepo(sourceDir, destRepoDir, dst).map(_ => ()) |> liftShell(repo)
    ))
  }

  /**
    * Updates the versions of all the specified artifacts of the list of git repos, who must be sbt
    * projects. It creates pull requests with the changes using the specified `pullRequest` function.
    */
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

  /** Lifts artifactory errors into updater errors.*/
  def liftArtifactory[T](result: Artifactory[T]): Updater[T] = result.leftMap {
    case e: ArtifactoryAuthenticationError => UpdaterArtifactoryAuthenticationError(e)
    case e: ArtifactoryUnexpectedError     => UpdaterArtifactoryUnexpectedError(e)
    case e: ArtifactoryParseError          => UpdaterArtifactoryParseError(e)
  }

  /** Lifts sbt errors into updater errors.*/
  def liftSbt[T](project: String)(result: Sbt[T]): Updater[T]   = result.leftMap(UpdaterSbtError.apply(project, _))

  /** Lifts git errors into updater errors.*/
  def liftGit[T](project: String)(result: Git[T]): Updater[T]   = result.leftMap(UpdaterGitError.apply(project, _))

  /** Lifts repo errors into updater errors.*/
  def liftRepo[T](project: String)(result: Repo[T]): Updater[T] = result.leftMap(UpdaterRepoError.apply(project, _))

  /** Lifts errors running the shell script into updater errors.*/
  def liftShell[T](project: String)(result: String \/ T): Updater[T] = result.leftMap(UpdaterShellError.apply(project, _))
}
