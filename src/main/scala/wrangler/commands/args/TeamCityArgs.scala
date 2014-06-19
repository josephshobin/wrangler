package wrangler.commands.args

import scalaz.Tag

import com.quantifind.sumac.validation._

import wrangler.api._

/** TeamCity configuration.*/
trait TeamCityArgs extends WranglerArgs {
  /** TeamCity base url.*/
  @Required
  var url: String              = _

  /** TeamCity parent project.*/
  @Required
  var project: String          = _

  /** TeamCity user.*/
  @Required
  var user: String             = _

  /** Optional TeamCity password.*/
  var password: Option[String] = None

  /** Type tagged url.*/
  lazy val turl   = Tag[String, TeamCityURLT](url)

  /** Type tagged project.*/
  lazy val tproject      = Tag[String, TeamCityProjectT](project)

  /** Type tagged user.*/
  lazy val tuser     = Tag[String, TeamCityUserT](user)

  /**
    * Prompts for password if password wasn't specified and returns it tagged as
    * `TeamCityPasswordT`.
    */
  lazy val tpassword = Tag[String, TeamCityPasswordT](password.getOrElse {
    System.console.readPassword("TeamCity pasword: ").mkString
  })
}
