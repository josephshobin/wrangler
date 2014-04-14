package wrangler.api

import scalaz._, Scalaz._

import org.json4s.{JValue, DefaultFormats}
import org.json4s.JsonDSL._

import wrangler.api.rest._

sealed trait GithubURLT
sealed trait GithubUserT
sealed trait GithubPasswordT
sealed trait GithubOrganisationT

object Github {
  type GithubURL          = String @@ GithubURLT
  type GithubUser         = String @@ GithubUserT
  type GithubPassword     = String @@ GithubPasswordT
  type GithubOrganisation = String @@ GithubOrganisationT

  implicit val formats = DefaultFormats

  def post(target: String, content: JValue)(implicit baseurl: GithubURL, user: GithubUser, password: GithubPassword): Repo[JValue] =
    Rest.post(baseurl + "/" + target, content, user, password)

  def get(target: String)(implicit baseurl: GithubURL, user: GithubUser, password: GithubPassword): Repo[JValue] =
    Rest.get(baseurl + "/" + target, user, password)

  def createRepo(repo: String, teamId: Int, priv: Boolean = true)(implicit org: GithubOrganisation, baseurl: GithubURL, user: GithubUser, password: GithubPassword)
      : Repo[JValue] = {
    post(s"orgs/$org/repos", (("name" -> repo) ~ ("team_id" -> teamId) ~ ("private" ->  priv)))
  }

  def listRepos(org: String)
    (implicit baseurl: GithubURL, user: GithubUser, password: GithubPassword)
      : Repo[List[String]] =
    get(s"orgs/$org/repos?per_page=100&type=all").map(s => 
      (s \\ "name").children.map(_.extract[String])
    )

  def pullRequest(repo: String, srcBranch: String, dstBranch: String, title: String, description: String)
  (implicit org: GithubOrganisation, baseurl: GithubURL, user: GithubUser, password: GithubPassword)
      : Repo[JValue] = {
    post(s"repos/$org/$repo/pulls", (
      ("title" -> title) ~
      ("body"  -> description) ~
      ("head"  -> srcBranch) ~
      ("base"  -> dstBranch)
    ))
  }

  def withAuthentication[T](command: GithubPassword => Repo[T]): (Repo[T], GithubPassword) = {
    implicit val password = Tag[String, GithubPasswordT](System.console.readPassword("Github Pasword: ").mkString)
    command(password) match {
      case -\/(Unauthorized(_)) => {
        println("Invalid Github password")
        withAuthentication(command)
      }
      case x                 => (x, password)
    }
  }

  def retryUnauthorized[T](password: GithubPassword, command: GithubPassword => Repo[T]): (Repo[T], GithubPassword) = {
    command(password) match {
      case -\/(Unauthorized(_)) => {
        println("Invalid Github password. Please try again")
        val newPassword = Tag[String, GithubPasswordT](System.console.readPassword("Github password: ").mkString)
        retryUnauthorized(newPassword, command)
      }
      case x => (x, password)
    }
  }


}
