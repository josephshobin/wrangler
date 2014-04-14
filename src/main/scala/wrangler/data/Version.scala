package wrangler.data

import wrangler.api.ParsedVersion

import scalaz.{Ordering => _, _}, Scalaz._

case class VersionParseError(line: String, error: String) {
  val msg = s"Failed to parse $line. $error"
}

sealed trait Version {
  def pretty: String
}

case class SemanticVersion(major: Int, minor: Int, patch: Int) extends Version {
  val pretty = s"$major.$minor.$patch"

  def incrementPatch: SemanticVersion = copy(patch = patch + 1)
  def incrementMinor: SemanticVersion = copy(minor = minor + 1, patch = 0)
  def incrementMajor: SemanticVersion = copy(major = major + 1, minor = 0, patch = 0)

  def increment(level: VersionLevel) = level match {
    case Major => incrementMajor
    case Minor => incrementMinor
    case Patch => incrementPatch
  }
}

case class DateVersion(major: Int, minor: Int, patch: Int, date: String) extends Version {
  val pretty = s"$major.$minor.$patch-$date"
}

case class CommishVersion(major: Int, minor: Int, patch: Int, date: String, commish: String) extends Version {
  def pretty = s"$major.$minor.$patch-$date-$commish"
}

object Version {
  implicit val VersionOrdering: Ordering[Version] = Ordering.by {
    case SemanticVersion(major, minor, patch)         => (major, minor, patch, Char.MaxValue.toString)
    case DateVersion(major, minor, patch, date)       => (major, minor, patch, date)
    case CommishVersion(major, minor, patch, date, _) => (major, minor, patch, date)
  }

  def parse(in: String): ParsedVersion[Version] = {
    \/.fromTryCatch {
      val ss = in.split("\\.|-")
      ss.length match {
        case 3 => SemanticVersion(ss(0).toInt, ss(1).toInt, ss(2).toInt)
        case 4 => DateVersion(ss(0).toInt, ss(1).toInt, ss(2).toInt, ss(3))
        case 5 => CommishVersion(ss(0).toInt, ss(1).toInt, ss(2).toInt, ss(3), ss(4))
      }
    }.leftMap(ex => VersionParseError(in, ex.toString))
  }
}

object SemanticVersion {
  def parse(in: String): ParsedVersion[SemanticVersion] = {
    \/.fromTryCatch {
      val ss = in.split("\\.")
      SemanticVersion(ss(0).toInt, ss(1).toInt, ss(2).toInt)
    }.leftMap(ex => VersionParseError(in, ex.toString))
  }
}

sealed trait VersionLevel
case object Major extends VersionLevel
case object Minor extends VersionLevel
case object Patch extends VersionLevel
