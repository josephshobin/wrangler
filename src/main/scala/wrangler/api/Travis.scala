package wrangler.api

import java.io.File

import scalaz._, Scalaz._

/**
  * Shells out to the travis client for ease of use.
  * Expects the travis client to be logged in.
  */
object Travis {
  /** Enables travis for a given github repo.*/
  def createBuild(repo: String): String \/ String =
    Util.run(List("travis", "enable", "-r", repo))

  /**
    * Encrypts and adds the specified environment variables to the .travis.yml for the specified
    * project.
    */
  def addSecureVariables(repoPath: String, variables: List[(String, String)]): String \/ List[String] =
    variables.map { case (k, v) =>
      Util.run(List("travis", "encrypt", s"$k=$v", "--add"), Some(new File(repoPath)))
    }.sequenceU
}


