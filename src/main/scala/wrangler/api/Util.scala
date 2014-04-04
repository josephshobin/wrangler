package wrangler.api

import java.io.File

import scala.util.control.NonFatal
import scala.util.Try
import scala.sys.process._

import scalaz._, Scalaz._

import Stash._

object Util {
  def getChoice[T](prompt: String, choices: Map[String, T]): T = {
    val out = choices.keys.toArray
    println(prompt)
    out.zipWithIndex.foreach { case (key, idx) => println(s"${idx+1}: $key") }
    
    Option(readLine("> "))
      .flatMap(s => safe(s.toInt - 1))
      .filter(s => s >= 0 && s < out.length)
      .flatMap(s => choices.get(out(s)))
      .getOrElse {
        println(s"Invalid choice. Please try again")
        getChoice(prompt, choices)
      }
  }

  def safe[T](thunk: => T): Option[T] = Try {
    thunk
  }.toOption

  def automator(repos: List[String], scriptPath: String, gitUrl: String, branch: String, title: String, description: String)
    (implicit project: StashProject, baseUrl: StashURL, user: StashUser, password: StashPassword)
      : List[String \/ String] = {
    repos.map(process(_, scriptPath, gitUrl, branch, title, description))
  }

  def process(repo: String, scriptPath: String, gitBaseUrl: String, branch: String, title: String, description: String)
    (implicit project: StashProject, baseurl: StashURL, user: StashUser, password: StashPassword)
      : String \/ String = {
    println(s"Processing $repo")
    val gitUrl = s"$gitBaseUrl/${repo}.git"
    val stashUrl = s"$baseurl/$repo"
    val dst = File.createTempFile("automater-", s"-$repo")
    dst.delete
    val r = for {
      g1 <- Git.clone(gitUrl, dst).leftMap(_.toString)
      g2 <- Git.createBranch(g1, branch).leftMap(_.toString)
      _  <- run(List("bash", scriptPath), Some(dst))
      _  <- Git.push(g2, branch).leftMap(_.toString)
      _  <- Stash.pullRequest(repo, branch, "master", title, description).leftMap(_.toString)
    } yield s"Updated $repo"
    r.leftMap(s"Failed to update $repo" + _)
  }

  def run(cmd: Seq[String], cwd: Option[File] = None): String \/ String = {
    val output = new StringBuilder
    val errors = new StringBuilder
    val logger = ProcessLogger(out => {output append out; errors append out}, err => errors append err)
    val ret = Process(cmd, cwd) ! logger

    if (ret == 0) output.toString.right
    else errors.toString.left
  }
}
