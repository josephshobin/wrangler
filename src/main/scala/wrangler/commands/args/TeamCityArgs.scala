package wrangler.commands.args

import scalaz.Tag

import com.quantifind.sumac.validation._

import wrangler.api._

trait TeamCityArgs extends WranglerArgs {
  @Required
  var url: String              = _
  @Required
  var project: String          = _
  @Required
  var user: String             = _

  var password: Option[String] = None

  lazy val turl   = Tag[String, TeamCityURLT](url)
  lazy val tproject      = Tag[String, TeamCityProjectT](project)
  lazy val tuser     = Tag[String, TeamCityUserT](user)

  lazy val tpassword = Tag[String, TeamCityPasswordT](password.getOrElse {
    System.console.readPassword("TeamCity pasword: ").mkString
  })
}
