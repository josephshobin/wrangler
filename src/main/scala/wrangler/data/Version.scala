package wrangler.data

import wrangler.api.ParsedVersion

import scalaz.{Ordering => _, _}, Scalaz._

case class VersionParseError(line: String, error: String) {
  val msg = s"Failed to parse $line. $error"
}

/** Well understood version.*/
sealed trait Version {
  /** Nicely formatted string representation.*/
  def pretty: String
}

/** Semantic version, major.minor.patch.*/
case class SemanticVersion(major: Int, minor: Int, patch: Int)
    extends Version {
  val pretty = s"$major.$minor.$patch"

  /** Bump patch level.*/
  def incrementPatch: SemanticVersion = copy(patch = patch + 1)

  /** Bump minor level.*/
  def incrementMinor: SemanticVersion = copy(minor = minor + 1, patch = 0)

  /** Bump major level.*/
  def incrementMajor: SemanticVersion = copy(major = major + 1, minor = 0, patch = 0)

  /** Increment specified version level.*/
  def increment(level: VersionLevel) = level match {
    case Major => incrementMajor
    case Minor => incrementMinor
    case Patch => incrementPatch
  }
}

/** Semantic version plus date major.minor.patch-date.*/
case class DateVersion(major: Int, minor: Int, patch: Int, date: String)
    extends Version {
  val pretty = s"$major.$minor.$patch-$date"
}

/** Semantic version plus date and short commit hash (commish) major.minor.patch-date-commish.*/
case class CommishVersion(major: Int, minor: Int, patch: Int, date: String, commish: String)
    extends Version {
  val pretty = s"$major.$minor.$patch-$date-$commish"
}

/** Version companion object.*/
object Version {
  /**
    * Defined ordering for versions based on numeric comparison of major then minor then patch and
    * if available lexicographic comparison of the date string. Versions without a date string 
    * precede versions with a date string. Commish is never used for comparison.
    */
  implicit val VersionOrdering: Ordering[Version] = Ordering.by {
    case SemanticVersion(major, minor, patch)         => (major, minor, patch, Char.MaxValue.toString)
    case DateVersion(major, minor, patch, date)       => (major, minor, patch, date)
    case CommishVersion(major, minor, patch, date, _) => (major, minor, patch, date)
  }

  /**
    * Parse version from string.
    * 
    *  - `1.0.1` is parsed as `SematicVersion`
    *  - `1.0.1-20140619134323` is parsed as `DateVersion`
    *  - `1.0.1-20140619134323-4fc1829` is parsed as `CommishVersion`
    */
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

/** SemanticVersion companion object.*/
object SemanticVersion {
  /** Parse semantic version e.g. `1.0.5`.*/
  def parse(in: String): ParsedVersion[SemanticVersion] = {
    \/.fromTryCatch {
      val ss = in.split("\\.")
      SemanticVersion(ss(0).toInt, ss(1).toInt, ss(2).toInt)
    }.leftMap(ex => VersionParseError(in, ex.toString))
  }
}

/** Version levels from semantic versioning such as Major, Minor, Patch.*/
sealed trait VersionLevel
case object Major extends VersionLevel
case object Minor extends VersionLevel
case object Patch extends VersionLevel
