package wrangler
package commands

import java.io.File

import scala.collection.JavaConverters._

import scalaz._, Scalaz._

import org.apache.felix.gogo.commands.{Argument => argument, Command => command, Option => option}
import org.apache.felix.service.command.CommandSession

import wrangler.api._

@command(scope = "omnia", name = "clone-existing", description = "Forks and clones an existing project")
class CloneExisting extends FunctionalAction {
  @option(required = true, name = "--repo", description = "Project to clone")
  var repo: String = null

  @option(required = true, name = "--user", description = "Stash user name")
  var userIn: String = null

  @option(required = true, name = "--stash-url", description = "Stash url")
  var stashUrlIn: String = null

  @option(required = true, name = "--git-url", description = "Git url")
  var gitUrl: String = null

  @option(required = true, name = "--project", description = "Stash base project")
  var projectIn: String = null


  def execute(session: CommandSession): AnyRef = run(session) {
    implicit val s = session

    implicit val url     = Tag[String, StashURLT](stashUrlIn)
    implicit val user    = Tag[String, StashUserT](userIn)
    implicit val project = Tag[String, StashProjectT](projectIn)

    val (result, pass) = Stash.withAuthentication(p => Stash.fork(repo)(project, url, user, p))
    implicit val password = pass

    for {
      _   <- result.leftMap(_.toString)
      _   <- Stash.forkSync(repo).leftMap(_.toString)
      git <- Git.clone(s"$gitUrl/~$user/$repo.git", repo).leftMap(_.toString)
      _   <- Git.addRemote(git, "upstream", s"$gitUrl/$project/$repo.git").leftMap(_.toString)
    } yield s"Successfully cloned $repo"

  }
}

