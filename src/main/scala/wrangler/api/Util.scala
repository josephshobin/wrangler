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

/** Assorted functions that don't have a place anywhere else.*/
object Util {
  /** Prompt for a choice.*/
  def getChoice[T](prompt: String, choices: Map[String, T]): T = {
    val out = choices.keys.toArray
    println(prompt)
    out.zipWithIndex.foreach { case (key, idx) => println(s"${idx+1}: $key") }
    
    Option(readLine("> "))
      .flatMap(s => \/.fromTryCatch(s.toInt - 1).toOption)
      .filter(s => s >= 0 && s < out.length)
      .flatMap(s => choices.get(out(s)))
      .getOrElse {
        println(s"Invalid choice. Please try again")
        getChoice(prompt, choices)
      }
  }

  /** Run the specified shell command. Optionally change the working directory.*/
  def run(cmd: Seq[String], cwd: Option[File] = None): String \/ String = {
    val output = new StringBuilder
    val errors = new StringBuilder
    val logger = ProcessLogger(
      out => {
        output append out
        errors append out
      },
      err => errors append err
    )

    if ((Process(cmd, cwd) ! logger) == 0) output.toString.right
    else errors.toString.left
  }
}
