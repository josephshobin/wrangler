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

import wrangler.data._
import wrangler.api.rest._

// Tags for Artifactory credentials.
sealed trait ArtifactoryUserT
sealed trait ArtifactoryPasswordT

sealed trait ArtifactoryError {
  def msg: String
}

case class ArtifactoryAuthenticationError(
  artifactory: String, repo: String, uri: String, user: String
) extends ArtifactoryError {
  val msg =
    s"Failed to authenticate as $user against Artifactory $artifactory for repo $repo. Uri: $uri"
}

case class ArtifactoryUnexpectedError(
  artifactory: String, repo: String, error: String
) extends ArtifactoryError {
  val msg =
    s"An unexpected error occurred talking to Artifactory $artifactory for repo $repo. $error"
}

case class ArtifactoryParseError(
  artifactory: String, repo: String, line: String, error: String
) extends ArtifactoryError {
  val msg = s"Failed to parse $line from $repo at $artifactory. $error"
}

/** API for making REST requests to Artifactory.*/
object Artifactory {
  implicit val formats = DefaultFormats

  type ArtifactoryUser     = String @@ ArtifactoryUserT
  type ArtifactoryPassword = String @@ ArtifactoryPasswordT

  /** Gets all artifacts in a specified repo. Returns the information as json.*/
  def listArtifacts(artifactory: String, repo: String)
    (implicit user: ArtifactoryUser, password: ArtifactoryPassword): Artifactory[JValue] = {
    Rest.get(s"$artifactory/api/storage/$repo?list&deep=1", user, password) |>
    liftRest(artifactory, repo, user)
  }

  /**
    * Gets the latest version of all artifacts in a repo. Can only parse versions such as
    * major.minor.patch[-date[-commish]]
    */
  def listLatest(artifactory: String, repo: String)
    (implicit user: ArtifactoryUser, password: ArtifactoryPassword): Artifactory[List[Artifact]] =
    listLatest(artifactory, List(repo))

  /**
    * Gets the latest version of all artifacts in the specified repos. Can only parse versions such
    * as major.minor.patch[-date[-commish]]
    */
  def listLatest(artifactory: String, repos: List[String])
    (implicit user: ArtifactoryUser, password: ArtifactoryPassword): Artifactory[List[Artifact]] = {
    repos.map { repo =>
      listArtifacts(artifactory, repo)
        .map(json =>
          (json \\ "uri")
            .children
            .tail
            .map(_.extract[String])
            .filter(_.endsWith(".jar"))
            .flatMap(parseVersion(artifactory, repo)(_).toList)
        )
    }.sequenceU.map(ls => getLatest(ls.flatten))
  }

  /**
    * Parse the artifact and version information from a uri returned by artifactory when
    * requesting all the artifacts.
    */
  def parseVersion(artifactory: String, repo: String)(uri: String): Artifactory[Artifact] = {
    val split = uri.drop(1).split("/").toVector
    val l     = split.length
    
    if (l < 3)
      ArtifactoryParseError(
        artifactory, repo, uri, "Does not have expected format .../group/name/version/package"
      ).left
    else {
       /*
        * The URI pattern identified from artifactory
        *  Pattern 1: /group/artifact/scala_2.10/sbt_0.13/versionStr/jars/etl-plugin.jar
        *  Pattern 2: /au/com/cba/omnia/artifact_2.10/versionStr/etl-util.jar
        * Check for the intial directory "au" to identify the release pattern
        */
      val isNotIvyRelease = split(0).equals("au")
      val versionStr      = if (isNotIvyRelease) split(l - 2)
                            else split(l - 3)
      val group           = if (isNotIvyRelease) split.slice(0, l - 3).mkString(".")
                            else split(0)
      val artifact        = if (isNotIvyRelease) split(l - 3)
                            else split(1)
      /* Regex pattern used to support 2.xx  Scala versions */
      val stripped        = if (artifact.matches(s".*_2.[0-9]{2}")) artifact.take(artifact.length - 5)
                            else artifact

      Version.parse(versionStr)
        .leftMap(e => ArtifactoryParseError(artifactory, repo, uri, e.msg))
        .map(Artifact(group, stripped, _))
    }
  }
  
  /** Only keep the latest versioned artifacts from a list of artifacts. */
  def getLatest(artifacts: List[Artifact]): List[Artifact] = {
    val map = artifacts.groupBy(a => (a.group, a.name))
    map.mapValues(_.max(Ordering.by[Artifact, Version](_.version))).values.toList
  }

  /** Lift Rest error into artifactory error.*/
  def liftRest[T](artifactory: String, repo: String, user: String)(result: Rest[T]): Artifactory[T] =
    result.leftMap {
      case Unauthorized(uri) => ArtifactoryAuthenticationError(artifactory, repo, uri, user)
      case e                 => ArtifactoryUnexpectedError(artifactory, repo, e.msg)
    }
}
