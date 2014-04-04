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
