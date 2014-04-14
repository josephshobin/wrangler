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

  def automator(
    repos: List[String], scriptPath: String, gitUrl: String, branch: String, title: String,
    description: String, runPR: String => String \/ Unit
  ): List[String \/ String] = {
    repos.map(process(_, scriptPath, gitUrl, branch, title, description, runPR))
  }

  def process(
    repo: String, scriptPath: String, gitBaseUrl: String, branch: String, title: String,
    description: String, runPR: String => String \/ Unit
  ): String \/ String = {
    println(s"Processing $repo")
    val gitUrl = s"$gitBaseUrl/${repo}.git"
        val dst = File.createTempFile("automater-", s"-$repo")
    dst.delete
    val r = for {
      g1 <- Git.clone(gitUrl, dst).leftMap(_.toString)
      g2 <- Git.createBranch(g1, branch).leftMap(_.toString)
      _  <- run(List("bash", scriptPath), Some(dst))
      _  <- Git.push(branch)(g2).leftMap(_.toString)
      _  <- runPR(repo)
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
