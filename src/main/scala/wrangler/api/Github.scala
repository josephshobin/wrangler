package wrangler.api

import scalaz._, Scalaz._

import com.ning.http.client.Response

import dispatch._, Defaults._
import dispatch.as.json4s._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

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

  def command(target: String, content: JValue)(implicit baseurl: GithubURL, user: GithubUser, password: GithubPassword): RestError \/ JValue = {
    val endpoint = url(baseurl + "/" + target)
    println(pretty(render(content)))
    val x = endpoint.as_!(user, password).addHeader("content-type", "application/json") << compact(render(content))
    println(x)
    val req = Http(x)

    println(req)
    req
      .either
      .apply
      .disjunction
      .leftMap(Error.apply)
      .flatMap(responseHandler)
  }

  def get(target: String)(implicit baseurl: GithubURL, user: GithubUser, password: GithubPassword): RestError \/ JValue = {
    val endpoint = url(baseurl + "/" + target)
    Http((endpoint.as_!(user, password)))(executor)
      .either
      .apply
      .disjunction
      .leftMap(Error.apply)
      .flatMap(responseHandler)
  }

  def responseHandler(response: Response): RestError \/ JValue = response.getStatusCode match {
    case c if c >= 200 && c < 300           => Json(response).right
    case c if Set(401, 403) contains c => Unauthorized.left
    case 404                                => NotFound(response.getResponseBody).left
    case c                                  => RequestError(c, Json(response)).left
  }

  def createRepo(repo: String, teamId: Int, priv: Boolean = true)(implicit org: GithubOrganisation, baseurl: GithubURL, user: GithubUser, password: GithubPassword)
      : RestError \/ JValue = {
    command(s"orgs/$org/repos", (("name" -> repo) ~ ("team_id" -> teamId) ~ ("private" ->  priv)))
  }

  def listRepos(org: String)
    (implicit baseurl: GithubURL, user: GithubUser, password: GithubPassword)
      : RestError \/ List[String] =
    get(s"orgs/$org/repos?per_page=100&type=all").map(s => 
      (s \\ "name").children.map(_.extract[String])
    )

  def withAuthentication[T](command: GithubPassword => RestError \/ T): (RestError \/ T, GithubPassword) = {
    implicit val password = Tag[String, GithubPasswordT](System.console.readPassword("Pasword: ").mkString)
    command(password) match {
      case -\/(Unauthorized) => {
        println("Invalid password")
        withAuthentication(command)
      }
      case x                 => (x, password)
    }
  }
}

object Test {
  def main(args: Array[String]): Unit = {
    implicit val url  = Tag[String, GithubURLT]("https://api.github.com")
    implicit val user = Tag[String, GithubUserT]("stephanh")
    implicit val org  = Tag[String, GithubOrganisationT]("CommBank")

    val (result, _) = Github.withAuthentication(p => Github.createRepo(args(0), 543398)(org, url, user, p))

    println(result)
  }
}
