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

package wrangler
package commands

import com.quantifind.sumac.ArgMain

import wrangler.api.{Stash, Github}
import wrangler.commands.args.StashOrGithubArgs

/** Arguments for `ListRepos`.*/
class ListReposArgs extends StashOrGithubArgs

/** List all the repos belonging to the specified stash project or github org.*/
object ListRepos extends ArgMain[ListReposArgs] {
  def main(args: ListReposArgs): Unit = {
    val result = 
      if (args.useStash) {
        println("Listing Stash repos")
        val stash = args.ostash.get
        val (result, _) = Stash.retryUnauthorized(
          stash.tpassword,
          p => Stash.listRepos(stash.project)(stash.tapiUrl, stash.tuser, p)
        )
        
        result
      } else {
        println("Listing Github repos")
        val github = args.ogithub.get
        val (result, _) = Github.retryUnauthorized(
          github.tpassword,
          p => Github.listRepos(github.org)(github.tapiUrl, github.tuser, p)
        )

        result
      }

    println(result.fold(_.msg, _.mkString("\n")))

    //Exit manually since dispatch hangs.
    sys.exit(0)
  }
}
