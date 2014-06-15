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

import scalaz.{Ordering => _, _}, Scalaz._

import dispatch._, Defaults._
import dispatch.as.json4s._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

sealed trait ArtifactoryUserT
sealed trait ArtifactoryPasswordT

case class Artifact(group: String, name: String, version: Version)

case class Version(major: Int, minor: Int, patch: Int, date: String, commish: Option[String]) {
  def pretty = commish.cata(
    c => s"$major.$minor.$patch-$date-$c",
    s"$major.$minor.$patch-$date"
  )
}

object Artifactory {
  implicit val formats = DefaultFormats

  type ArtifactoryUser     = String @@ ArtifactoryUserT
  type ArtifactoryPassword = String @@ ArtifactoryPasswordT


  implicit val VersionOrdering: Ordering[Version] = Ordering.by(v => (v.major, v.minor, v.patch, v.date))

  def listArtifacts(artifactory: String, repo: String)
    (implicit user: ArtifactoryUser, password: ArtifactoryPassword): Throwable \/ JValue = {
    Http(url(s"$artifactory/api/storage/$repo?list&deep=1").as_!(user, password) OK Json)
      .either
      .apply
      .disjunction
  }

  def listLatest(artifactory: String, repo: String)
    (implicit user: ArtifactoryUser, password: ArtifactoryPassword): Throwable \/ List[Artifact] = {
    listArtifacts(artifactory, repo)
      .map { json =>
        val versions = (json \\ "uri")
          .children
          .tail
          .map(_.extract[String])
          .filter(_.endsWith(".jar"))
          .flatMap(parseVersion(_).toList)
        getLatest(versions)
      }
  }

  def parseVersion(uri: String): String \/ Artifact = {
    val split = uri.drop(1).split("/").toVector
    val l     = split.length
    
    if (l < 3) s"Does not have expected format $uri".left
    else {
      val versionStr = split(l - 2)
      val group = split.slice(0, l - 3).mkString(".")
      val artifact = split(l - 3)
      val stripped = if (artifact.endsWith("_2.10")) artifact.take(artifact.length - 5) else artifact

      Util.safe {
        val ss = versionStr.split("\\.|-")
        val commish = if (ss.length > 4) Some(ss(4)) else None
        Version(ss(0).toInt, ss(1).toInt, ss(2).toInt, ss(3), commish)
      }.cata(Artifact(group, stripped, _).right, s"Failed to parse version $versionStr".left)
    }
  }

  def getLatest(artifacts: List[Artifact]): List[Artifact] = {
    val map = artifacts.groupBy(a => (a.group, a.name))
    map.mapValues(_.max(Ordering.by[Artifact, Version](_.version))).values.toList
  }
}
