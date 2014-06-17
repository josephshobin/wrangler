package wrangler.commands.args

import scalaz.Tag

import wrangler.api._

trait RepoArgs extends WranglerArgs {
  val github = new GithubArgs{}
  val stash  = new StashArgs{}

  lazy val ogithub = Option(github.user).map(_ => github)
  lazy val ostash = Option(stash.user).map(_ => stash)

  addValidation(require(ogithub.isDefined || ostash.isDefined, "Need to provide configuration for either github or statsh"))
}

trait GithubArgs extends WranglerArgs {
  var org: String              = _
  var apiUrl: String           = _
  var gitUrl: String           = _
  var teamid: Int              = _
  var user: String             = _

  var password: Option[String] = None

  addValidation {
    val args = List(org, apiUrl, gitUrl, user)
    require(args.forall(_ == null) || args.forall(_ != null), s"Need to set all github arguments if using github")
  }

  lazy val tapiUrl   = Tag[String, GithubURLT](apiUrl)
  lazy val torg      = Tag[String, GithubOrganisationT](org)
  lazy val tuser     = Tag[String, GithubUserT](user)

  lazy val tpassword = Tag[String, GithubPasswordT](password.getOrElse {
    System.console.readPassword("Github pasword: ").mkString
  })
}

trait StashArgs extends WranglerArgs {
  var project: String          = _
  var apiUrl: String           = _
  var gitUrl: String           = _
  var user: String             = _
  var reviewers: List[String]  = List()

  var password: Option[String] = None

  addValidation {
    val args = List(project, apiUrl, gitUrl, user)
    require(args.forall(_ == null) || args.forall(_ != null), "Need to set all stash arguments if using stash")
  }

  lazy val tapiUrl   = Tag[String, StashURLT](apiUrl)
  lazy val tproject  = Tag[String, StashProjectT](project)
  lazy val tuser     = Tag[String, StashUserT](user)

  lazy val tpassword = Tag[String, StashPasswordT](password.getOrElse {
    System.console.readPassword("Stash pasword: ").mkString
  })
}
