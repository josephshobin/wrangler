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

package wrangler.commands

import scalaz._, Scalaz._

import com.quantifind.sumac.ArgMain
import com.quantifind.sumac.validation._

import wrangler.api._
import wrangler.commands.args._

/** Arguments for create project command.*/
class CreateTeamCityProjectArgs extends WranglerArgs {
  /** Name of the project/repo.*/
  @Required
  var repo: String = _

  var groupName: Option[String] = None

  /** Name of teamcity build template to use.*/
  var teamcityTemplate: String = _

  val teamcity = new TeamCityArgs {}

  addValidation {
    require(teamcityTemplate != null, "Need to provided TeamCity template name to use for build")
  }
}

/**
  * Creates a new team city project based on the template.
  *
  *  1. Creates a TeamCity project using the specified template,
  */
object CreateTeamCityProject extends ArgMain[CreateTeamCityProjectArgs] {
  /** Run the command. */
  def main(args: CreateTeamCityProjectArgs): Unit = {
    println("Creating team city build")

    val repo = args.repo

    val teamcity = args.teamcity
    implicit val tcUrl      = teamcity.turl
    implicit val tcProject  = teamcity.tproject
    implicit val tcUser     = teamcity.tuser
    implicit val tcPassword = Tag[String, TeamCityPasswordT](teamcity.tpassword)

    val Array(group, name) = repo.split("\\.")
    val groupName = args.groupName.getOrElse(group)

    TeamCity.createBuild(groupName, repo, args.teamcityTemplate).fold(
      e => {
        println(e.msg)
        sys.exit(1)
      },
      identity
    )
    //Exit manually since dispatch hangs.
    println("Created team city build")
    sys.exit(0)
  }
}
