package wrangler.api

import java.io.File

import scalaz._, Scalaz._

/**
  * Shells out to the travis client for ease of use.
  * Expects the travis client to be logged in.
  */
object Travis {
  def createBuild(repo: String): String \/ String =
    Util.run(List("travis", "enable", "-r", repo))

  def addSecureVariables(repoPath: String, variables: List[(String, String)]): String \/ List[String] =
    variables.map { case (k, v) =>
      Util.run(List("travis", "encrypt", s"$k=$v", "--add"), Some(new File(repoPath)))
    }.sequenceU
}


