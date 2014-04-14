package wrangler.commands.args

import com.quantifind.sumac.FieldArgs

import scalaz.Tag

import wrangler.api._

trait ArtifactoryArgs extends FieldArgs {
  var url:      String = _
  var user:     String = _
  var repos:    List[String] = _
  var password: Option[String] = None

  addValidation {
    val args = List(url, user, repos)
    require(args.forall(_ == null) || args.forall(_ != null), s"Need to set all artifactory arguments if using artifactory")
  }

  lazy val tuser = Tag[String, ArtifactoryUserT](user)
  lazy val tpassword = Tag[String, ArtifactoryPasswordT](password.getOrElse {
    System.console.readPassword("Artifactory Pasword: ").mkString
  })
}

trait MultiplArtifactoriesArgs extends WranglerArgs {
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
