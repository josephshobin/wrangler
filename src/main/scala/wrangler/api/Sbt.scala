package wrangler.api

import java.io.File

import sbt._, Path._

import scalaz._, Scalaz._

import wrangler.data._

sealed trait SbtError {
  def msg: String
}

case class SbtNoVersionString(project: SbtProject) extends SbtError {
  val msg = s"Could not find version string for sbt project ${project.path}. Looked at ${project.versionFile}"
}

case class SbtUnexpectedError(project: SbtProject, exception: Throwable) extends SbtError {
  val msg = s"Unexpected error for sbt project ${project.path}. ${exception.toString}"
}

case class SbtVersionParseError(project: SbtProject, line: String, error: String) extends SbtError {
  val msg = s"Failed to parse version $line for sbt project ${project.path}. $error"
}

/** API around interacting with a specific sbt project.*/
case class SbtProject(dir: File) {
  val path = dir.getPath

  lazy val buildFiles = (dir ** "*.sbt").get ++ (dir / "project" * "*.scala").get
  lazy val versionFile = dir / "version.sbt"
  lazy val pluginSbt = dir / "project" / "plugins.sbt"

  /**
    * Gets the version of this sbt project.
    * It expects that the version is specified in version.sbt and only supports
    * semantic versioning.
    */
  def getVersion: Sbt[SemanticVersion] = \/.fromTryCatch {
    IO.readLines(versionFile)
      .find(_.startsWith("version in ThisBuild"))
      .map { s =>
        val Array(_, versionStr) = s.split(":=")
        SemanticVersion.parse(versionStr.trim.tail.init)
      }.cata(_.right, SbtNoVersionString(this).left)
      .flatMap(liftParsedVersion(_))
  }.leftMap[SbtError](SbtUnexpectedError.apply(this, _)).join[SemanticVersion]

  /** Increments the semantic version in version.sbt by the specified level (major, minor, patch).*/
  def incrementVersion(level: VersionLevel): Sbt[SbtProject] = \/.fromTryCatch {
    getVersion.map { version =>
      IO.writeLines(versionFile, IO.readLines(versionFile).map(l =>
        if (l.startsWith("version in ThisBuild"))
          s"""version in ThisBuild := "${version.increment(level).pretty}""""
        else l
      ))

      this
    }
  }.leftMap[SbtError](SbtUnexpectedError.apply(this, _)).join[SbtProject]

 /** Updates the versions of the specified dependencies in the project to the specified versions.*/
  def updateDependencies(updates: List[GenericArtifact]): Sbt[SbtProject] = \/.fromTryCatch {
    buildFiles.map(file =>
      IO.writeLines(
        file,
        IO.readLines(file)
          .map(line => updates.foldLeft(line)((l, a)=> SbtProject.updateVersion(l, a)))
      )
    )

    this
  }.leftMap(SbtUnexpectedError.apply(this, _))

  /** Lifts version parsing errors into sbt errors.*/
  def liftParsedVersion[T](v: ParsedVersion[T]): Sbt[T] = v.leftMap {
    case VersionParseError(l, e) => SbtVersionParseError(this, l, e)
  }
}

object SbtProject {
  /** Opens the specified sbt project.*/
  def apply(path: String): SbtProject = SbtProject(new File(path))

  /** 
    * Returns a new string where the version of the specified artifact in the input is replaced
    * with the specified version. Otherwise the input is unchanged.
    */
  def updateVersion(l: String, artifact: GenericArtifact): String = {
    def camelCase(name: String) = {
      val words = name.split("[-_]")
      words.head + words.tail.map(_.capitalize).mkString("")
    }

    // replace version in: val myArtifactVersion = "xxx"
    // (note that in this case only the artifact name is considered, not the group)
    def updateVal(l: String) = {
      val camelName = camelCase(artifact.name)
      if (l.trim.startsWith(s"val ${camelName}Version"))
        l.replaceAll(""""[^"]*"""", s""""${artifact.version}"""")
      else l
    }

    // replace version in: "my.group" % "my-artifact" % "xxx"
    //                 or: "my.group" %% "my-artifact" % "xxx"
    def updateExplicit(l: String) = {
      val findRegex = s""""${artifact.group}"( *%%? *)"${artifact.name}"( *% *)"[^"]+""""
      val replaceRegex = s""""${artifact.group}"$$1"${artifact.name}"$$2"${artifact.version}""""
      l.replaceAll(findRegex, replaceRegex)
    }

    // replace version in: depend.omnia("my-artifact", "xxx")
    //                 or: depend.omnia("my-artifact", "xxx", "my-config")
    def updateDepend(l: String) = {
      if (artifact.group == "au.com.cba.omnia") {
        val findRegex2 = s"""depend.omnia\\("${artifact.name}"( *, )"[^"]+""""
        val replaceRegex2 = s"""depend.omnia("${artifact.name}"$$1"${artifact.version}""""
        l.replaceAll(findRegex2, replaceRegex2)
      } else l
    }

    val l2 = l |> updateVal |> updateExplicit |> updateDepend

    if (l != l2) {
      // print a "diff"-like summary of the change, as feedback to the user
      println("< " + l)
      println("> " + l2)
      println("--")
    }
    l2
  }
}
