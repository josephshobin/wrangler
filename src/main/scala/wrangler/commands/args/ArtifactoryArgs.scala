package wrangler.commands.args

import com.quantifind.sumac.FieldArgs

import scalaz.Tag

import wrangler.api._

/** Artifactory configuration.*/
trait ArtifactoryArgs extends FieldArgs {
  /** Artifactory base url.*/
  var url:      String = _

  /** Artifactory artifact repos.*/
  var repos:    List[String] = _

  /** Artifactory user.*/
  var user:     String = _

  /** Optional Artifactory password.*/
  var password: Option[String] = None

  addValidation {
    val args = List(url, user, repos)
    require(args.forall(_ == null) || args.forall(_ != null), s"Need to set all artifactory arguments if using artifactory")
  }

  /** User tagged as `ArtifactorUserT`.*/
  lazy val tuser = Tag[String, ArtifactoryUserT](user)

  /**
    * Prompts for password if password wasn't specified and returns it tagged as
    * `ArtifactoryPasswordT`.
    */
  lazy val tpassword = Tag[String, ArtifactoryPasswordT](password.getOrElse {
    System.console.readPassword("Artifactory Password: ").mkString
  })
}

/** 
  * Arguments for up to 3 artifactory configurations, `artifactory, artifactory2, artifactory3`.
  * Artifactory needs to be specified.
  */
trait MultipleArtifactoriesArgs extends WranglerArgs {
  val artifactory = new ArtifactoryArgs{}
  val artifactory2 = new ArtifactoryArgs{}
  val artifactory3 = new ArtifactoryArgs{}

  lazy val artifactories = List(
    Option(artifactory.url).map(_ => artifactory),
    Option(artifactory2.url).map(_ => artifactory2),
    Option(artifactory3.url).map(_ => artifactory3)
  ).flatten

  addValidation {
    require(artifactory.url != null, "Need to provide artifactory configuration")
  }
}
