package wrangler
package commands

import java.io.File

import scala.collection.JavaConverters._

import scalaz._, Scalaz._

import org.apache.felix.gogo.commands.{Argument => argument, Command => command, Option => option}
import org.apache.felix.service.command.CommandSession

import wrangler.api._

@command(scope = "omnia", name = "stash-list-repos", description = "List stash repos")
class ListStashRepos extends FunctionalAction {
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

@command(scope = "omnia", name = "github-list-repos", description = "List github repos")
class ListGithubRepos extends FunctionalAction {
  @option(required = true, name = "--user", description = "Github user name")
  var userIn: String = null

  @option(name = "--github-url", description = "Github url", required = true)
  var githubUrlIn: String = null

  @option(name = "--org", description = "Github organisation", required = true)
  var org: String = null


  def execute(session: CommandSession): AnyRef = run(session) {
    implicit val s = session

    implicit val url     = Tag[String, GithubURLT](githubUrlIn)
    implicit val user    = Tag[String, GithubUserT](userIn)

    val (result, _) = Github.withAuthentication(p => Github.listRepos(org)(url, user, p))

    result.map(_.mkString("\n")).leftMap(_.toString)
  }
}

