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

import wrangler.api.rest._

// Tags for stash config.
sealed trait StashURLT
sealed trait StashUserT
sealed trait StashPasswordT
sealed trait StashProjectT

/** API for making REST requests to Stash.*/
object Stash {
  type StashURL      = String @@ StashURLT
  type StashUser     = String @@ StashUserT
  type StashPassword = String @@ StashPasswordT
  type StashProject  = String @@ StashProjectT

  // Default json formats.
  implicit val formats = DefaultFormats

  /** Does an authenticated POST.*/
  def post(target: String, content: JValue)
    (implicit baseurl: StashURL, user: StashUser, password: StashPassword): Repo[JValue] =
    Rest.post(baseurl + "/" + target, content, user, password)

  /** Does an authenticated GET.*/
  def get(target: String)
    (implicit baseurl: StashURL, user: StashUser, password: StashPassword): Repo[JValue] =
    Rest.get(baseurl + "/" + target, user, password)

  /** Creates a new repo. Allows it to be forked by default.*/
  def createRepo(repo: String, forkable: Boolean = true)
    (implicit project: StashProject, baseurl: StashURL, user: StashUser, password: StashPassword)
      : Repo[JValue] =
    post(s"api/latest/projects/$project/repos", (("name" -> repo) ~ ("forkable" ->  true)))

  /** Forks an existing repo to your personal project.*/
  def fork(repo: String)
    (implicit project: StashProject, baseurl: StashURL, user: StashUser, password: StashPassword)
      : Repo[JValue] = {
    post(s"api/latest/projects/$project/repos/$repo", ("slug" -> repo)) match {
      case -\/(RequestError(_, 409, msg))
          if (Json(msg) \\ "message").extract[String]
               .startsWith("This repository URL is already taken by")
          => Json(msg).right
      case x @ -\/(RequestError(_, 409, msg)) => {println(Json(msg) \\ "message"); x}
      case x => x
    }

  }

  /** Enables fork syncing on a personal project.*/
  def forkSync(repo: String)
    (implicit project: StashProject, baseurl: StashURL, user: StashUser, password: StashPassword)
      : Repo[JValue] = {
    post(s"sync/latest/projects/~$user/repos/$repo", ("enabled" -> true))
  }

  /** Creates a pull request from a personal repo to another repo.*/
  def pullRequestFromPersonal(
    srcRepo: String, srcBranch: String, dstRepo: String, dstBranch: String,
    title: String, description: String, reviewers: List[String]
  )(implicit project: StashProject, baseurl: StashURL, user: StashUser, password: StashPassword)
      : Repo[JValue] = {
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
          ("reviewers" -> reviewers.map(name => ("user" -> ("name" -> name))))
      )
    post(s"api/latest/projects/$project/repos/$dstRepo/pull-requests", content)
  }

  /** Creates a pull request from one branch to another branch in the same repo.*/
  def pullRequest(
    repo: String, srcBranch: String, dstBranch: String,
    title: String, description: String,reviewers: List[String]
  )(implicit project: StashProject, baseurl: StashURL, user: StashUser, password: StashPassword)
      : Repo[JValue] = {
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
          ("reviewers" -> reviewers.map(name => ("user" -> ("name" -> name))))
      )

    post(s"api/1.0/projects/$project/repos/$repo/pull-requests", content)
  }

  /**
    * Creates a new repo
    * 
    * It does that by:
    * 
    *  1. Creating the repo
    *  1. Forking it
    *  1. Enabling fork sync
    */
  def setupNewRepo(repo: String)
    (implicit project: StashProject, baseurl: StashURL, user: StashUser, password: StashPassword)
      : Repo[String] = {
    for {
      _ <- Stash.createRepo(repo)
      _ <- Stash.fork(repo)
      _ <- Stash.forkSync(repo)
    } yield s"Created Stash repo $repo"
  }

  /** Lists all the repos in the specified project.*/
  def listRepos(project: String)
    (implicit baseurl: StashURL, user: StashUser, password: StashPassword)
      : Repo[List[String]] =
    get(s"api/latest/projects/$project/repos?limit=1000").map(s => 
      (s \\ "slug").children.map(_.extract[String])
    )

  /**
    * Prompts for a password and performs the supplied request using that password.
    * If the request fails authentication it prompts for a new password and retries.
    */
  def withAuthentication[T](command: StashPassword => Repo[T]): (Repo[T], StashPassword) = {
    val password =
      Tag[String, StashPasswordT](System.console.readPassword("Stash Pasword: ").mkString)
    
    command(password) match {
      case -\/(Unauthorized(_)) => {
        println("Invalid stash password")
        withAuthentication(command)
      }
      case x                 => (x, password)
    }
  }

  /**
    * Performs the supplied request with the supplied command.
    * If the request fails it will prompt for a different password and retry.
    */
  def retryUnauthorized[T](password: StashPassword, command: StashPassword => Repo[T])
      : (Repo[T], StashPassword) = {
    command(password) match {
      case -\/(Unauthorized(_)) => {
        println("Invalid Stash password. Please try again")
        val newPassword =
          Tag[String, StashPasswordT](System.console.readPassword("Stash password: ").mkString)

        retryUnauthorized(newPassword, command)
      }
      case x => (x, password)
    }
  }
}
