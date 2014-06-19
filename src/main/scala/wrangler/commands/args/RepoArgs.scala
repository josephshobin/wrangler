package wrangler.commands.args

import scalaz.Tag

import wrangler.api._

/** Arguments for Github or Stash arguments. One and only one must be specified.*/
trait RepoArgs extends WranglerArgs {
  val github = new GithubArgs{}
  val stash  = new StashArgs{}

  lazy val ogithub = Option(github.user).map(_ => github)
  lazy val ostash = Option(stash.user).map(_ => stash)

  addValidation(require(
      ogithub.isDefined || ostash.isDefined, "Need to provide configuration for Github or Stash"
  ))
}

/** Arguments for Github.*/
trait GithubArgs extends WranglerArgs {
  /** Org that owns the repo.*/
  var org: String              = _

  /** Github base api url.*/
  var apiUrl: String           = _

  /** Github base git url to clone repos.*/
  var gitUrl: String           = _

  /** Github team id.*/
  var teamid: Int              = _

  /** Github user.*/
  var user: String             = _

  /** Optional github password.*/
  var password: Option[String] = None

  addValidation {
    val args = List(org, apiUrl, gitUrl, user)
    require(
      args.forall(_ == null) || args.forall(_ != null),
      s"Need to set all github arguments if using github"
    )
  }

  /** Type tagged api url.*/
  lazy val tapiUrl   = Tag[String, GithubURLT](apiUrl)

  /** Type tagged org.*/
  lazy val torg      = Tag[String, GithubOrganisationT](org)

  /** Type tagged user.*/
  lazy val tuser     = Tag[String, GithubUserT](user)

  /**
    * Prompts for password if password wasn't specified and returns it tagged as
    * `GithubPasswordT`.
    */
  lazy val tpassword = Tag[String, GithubPasswordT](password.getOrElse {
    System.console.readPassword("Github pasword: ").mkString
  })
}

/** Arguments for Stash.*/
trait StashArgs extends WranglerArgs {
  var project: String          = _

  /** Stash base api url.*/
  var apiUrl: String           = _

  /** Stash base git url to clone repos.*/
  var gitUrl: String           = _

  /** Reviewers to add to a Stash pull request.*/
  var reviewers: List[String]  = List()

  /** Stash user.*/
  var user: String             = _

  /** Optional github password.*/
  var password: Option[String] = None

  addValidation {
    val args = List(project, apiUrl, gitUrl, user)
    require(
      args.forall(_ == null) || args.forall(_ != null),
      "Need to set all stash arguments if using stash"
    )
  }

  /** Type tagged api url.*/
  lazy val tapiUrl   = Tag[String, StashURLT](apiUrl)

  /** Type tagged project.*/
  lazy val tproject  = Tag[String, StashProjectT](project)

  /** Type tagged user.*/
  lazy val tuser     = Tag[String, StashUserT](user)

  /**
    * Prompts for password if password wasn't specified and returns it tagged as
    * `StashPasswordT`.
    */
  lazy val tpassword = Tag[String, StashPasswordT](password.getOrElse {
    System.console.readPassword("Stash pasword: ").mkString
  })
}
