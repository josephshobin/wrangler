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

import scalaz._, Scalaz._

/** API around giter8, the project template tool.*/
object Giter8 {
  /** Deploy the named template with the given name and parameters.*/
  def deployTemplate(template: String, name: String, params: Map[String, String], cwd: Option[File] = None): String \/ String = {

    val source = name.substring((name.indexOf(".") + 1), name.indexOf("-"))
    val domain = name.substring(name.indexOf("-") + 1)

    val cmd = Seq("g8", template, s"--source=$source", s"--domain=$domain" , s"--name=$name") ++ params.toList.map { case (k, v) => s"--$k=$v" }

    for {
      _ <- Util.run(cmd, cwd)
      _ <- Util.run(List("chmod" , "+x", s"$name/sbt"), cwd) // Set execute permission on sbt script.
    } yield s"Created $name"
  }
}
