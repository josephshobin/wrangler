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

sealed trait TeamCityURLT
sealed trait TeamCityUserT
sealed trait TeamCityPasswordT
sealed trait TeamCityProjectT

object TeamCity {
  type TeamCityURL      = String @@ TeamCityURLT
  type TeamCityUser     = String @@ TeamCityUserT
  type TeamCityPassword = String @@ TeamCityPasswordT
  type TeamCityProject  = String @@ TeamCityProjectT

  implicit val formats = DefaultFormats

  def postText(target: String, content: String)(implicit baseurl: TeamCityURL, user: TeamCityUser, password: TeamCityPassword): RestError \/ JValue = {
    val endpoint = url(baseurl + "/" + target)
    Http((endpoint.as_!(user, password).addHeader("content-type", "text/plain").addHeader("Accept", "application/json") << content))
      .either
      .apply
      .disjunction
      .leftMap(Error.apply)
      .flatMap(responseHandler)
  }

  def putText(target: String, content: String)(implicit baseurl: TeamCityURL, user: TeamCityUser, password: TeamCityPassword): RestError \/ JValue = {
    val endpoint = url(baseurl + "/" + target).PUT
    Http((endpoint.as_!(user, password).addHeader("content-type", "text/plain").addHeader("Accept", "application/json") << content))
      .either
      .apply
      .disjunction
      .leftMap(Error.apply)
      .flatMap(responseHandler)
  }


  def get(target: String)(implicit baseurl: TeamCityURL, user: TeamCityUser, password: TeamCityPassword): RestError \/ JValue = {
    val endpoint = url(baseurl + "/" + target)
    Http((endpoint.as_!(user, password).addHeader("Accept", "application/json")))(executor)
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
    case c                        => {println(response.getResponseBody); RequestError(c, Json(response)).left }
  }

  def createBuild(group: String, name: String, template: String)
    (implicit project: TeamCityProject, baseurl: TeamCityURL, user: TeamCityUser, password: TeamCityPassword)
      : RestError \/ JValue = {
    val id = name.replaceAll("\\-|\\.", "")
    for {
      _ <- TeamCity.postText(s"projects/id:${project}_${group}/buildTypes", name)
      _ <- TeamCity.putText(s"buildTypes/id:${project}_${group}_$id/template", s"id:${project}_${template}")
    } yield s"Created TeamCity build for $group.$name"
  }
}
