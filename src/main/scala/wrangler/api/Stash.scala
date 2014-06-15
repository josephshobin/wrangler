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

package wrangler.api

import scalaz._, Scalaz._

import com.ning.http.client.Response

import dispatch._, Defaults._
import dispatch.as.json4s._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

sealed trait StashURLT
sealed trait StashUserT
sealed trait StashPasswordT
sealed trait StashProjectT

sealed trait RestError
case object Unauthorized extends RestError {
  override def toString = s"Stash request: 401 Unauthorized"
}
case class RequestError(code: Int, msg: JValue) extends RestError {
  override def toString = s"Stash RequestError($code, ${pretty(render(msg))})"
}
case class NotFound(msg: String) extends RestError
case class Error(exception: Throwable) extends RestError




object Stash {
  type StashURL      = String @@ StashURLT
  type StashUser     = String @@ StashUserT
  type StashPassword = String @@ StashPasswordT
  type StashProject  = String @@ StashProjectT

  implicit val formats = DefaultFormats

  def command(target: String, content: JValue)(implicit baseurl: StashURL, user: StashUser, password: StashPassword): RestError \/ JValue = {
    val endpoint = url(baseurl + "/" + target)
    Http((endpoint.as(user, password).addHeader("content-type", "application/json") << compact(render(content))))
      .either
      .apply
      .disjunction
      .leftMap(Error.apply)
      .flatMap(responseHandler)
  }

  def get(target: String)(implicit baseurl: StashURL, user: StashUser, password: StashPassword): RestError \/ JValue = {
    val endpoint = url(baseurl + "/" + target)
    Http((endpoint.as(user, password)))(executor)
      .either
      .apply
      .disjunction
      .leftMap(Error.apply)
      .flatMap(responseHandler)
  }

  def responseHandler(response: Response): RestError \/ JValue = response.getStatusCode match {
    case c if c >= 200 && c < 300 => Json(response).right
    case 401                      => Unauthorized.left
    case 404                      => NotFound(response.getResponseBody).left
    case c                        => RequestError(c, Json(response)).left
  }

  def createRepo(repo: String, forkable: Boolean = true)(implicit project: StashProject, baseurl: StashURL, user: StashUser, password: StashPassword)
      : RestError \/ JValue = {
    command(s"api/latest/projects/$project/repos", (("name" -> repo) ~ ("forkable" ->  true)))
  }

  def fork(repo: String)
    (implicit project: StashProject, baseurl: StashURL, user: StashUser, password: StashPassword)
      : RestError \/ JValue = {
    command(s"api/latest/projects/$project/repos/$repo", ("slug" -> repo)) match {
      case -\/(RequestError(409, msg)) if (msg \\ "message").extract[String].startsWith("This repository URL is already taken by")
          => msg.right
      case x @ -\/(RequestError(409, msg)) => {println(msg \\ "message"); x}
      case x => x
    }

  }

  def forkSync(repo: String)
    (implicit project: StashProject, baseurl: StashURL, user: StashUser, password: StashPassword)
      : RestError \/ JValue = {
    command(s"sync/latest/projects/~$user/repos/$repo", ("enabled" -> true))
  }

  def pullRequestFromPersonal(srcRepo: String, srcBranch: String, dstRepo: String, dstBranch: String, title: String, description: String)
  (implicit project: StashProject, baseurl: StashURL, user: StashUser, password: StashPassword): RestError \/ JValue = {
    val content =
      (
        ("title" -> title) ~
          ("description" -> description) ~
          ("state" -> "OPEN") ~
          ("fromRef" ->
            ("id" -> s"refs/heads/${srcBranch}") ~
            ("repository" ->
              ("slug" -> srcRepo) ~
              ("name" -> "null") ~
              ("project" ->
                ("key" -> s"~${user}".toUpperCase)
              )
            )
          ) ~
          ("toRef" ->
            ("id" -> s"refs/heads/${dstBranch}") ~
            ("repository" ->
              ("slug" -> dstRepo) ~
              ("name" -> "null") ~
              ("project" ->
                ("key" -> project.toUpperCase)
              )
            )
          ) ~
          ("reviewers" -> List())
      )

    command(s"api/latest/projects/$project/repos/$dstRepo/pull-requests", content)
  }

  def pullRequest(repo: String, srcBranch: String, dstBranch: String, title: String, description: String)
  (implicit project: StashProject, baseurl: StashURL, user: StashUser, password: StashPassword): RestError \/ JValue = {
    val content =
      (
        ("title" -> title) ~
        ("description" -> description) ~
        ("state" -> "OPEN") ~
        ("fromRef" ->
          ("id" -> s"refs/heads/${srcBranch}") ~
          ("repository" ->
            ("slug" -> repo) ~
            ("name" -> null) ~
            ("project" ->
              ("key" -> project.toUpperCase)
            )
          )
        ) ~
        ("toRef" ->
          ("id" -> s"refs/heads/${dstBranch}") ~
          ("repository" ->
            ("slug" -> repo) ~
            ("name" -> null) ~
            ("project" ->
              ("key" -> project.toUpperCase)
            )
          )
        ) ~
        ("reviewers" -> List(
          ("user" -> ("name" -> "hoermast")),
          ("user" -> ("name" -> "andersqu")),
          ("user" -> ("name" -> "rouesnla")),
          ("user" -> ("name" -> "lippmebe"))
        ))
      )

    command(s"api/1.0/projects/$project/repos/$repo/pull-requests", content)
  }

  def setupNewRepo(repo: String)
    (implicit project: StashProject, baseurl: StashURL, user: StashUser, password: StashPassword)
      : RestError \/ String = {
    for {
      _ <- Stash.createRepo(repo)
      _ <- Stash.fork(repo)
      _ <- Stash.forkSync(repo)
    } yield s"Created Stash repo $repo"
  }

  def listRepos(project: String)
    (implicit baseurl: StashURL, user: StashUser, password: StashPassword)
      : RestError \/ List[String] =
    get(s"api/latest/projects/$project/repos?limit=1000").map(s => 
      (s \\ "slug").children.map(_.extract[String])
    )

  def withAuthentication[T](command: StashPassword => RestError \/ T): (RestError \/ T, StashPassword) = {
    implicit val password = Tag[String, StashPasswordT](System.console.readPassword("Stash password: ").mkString)
    command(password) match {
      case -\/(Unauthorized) => {
        println("Invalid Stash password")
        withAuthentication(command)
      }
      case x                 => (x, password)
    }
  }
}
