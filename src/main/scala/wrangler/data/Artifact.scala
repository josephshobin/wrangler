package wrangler.data

/** Maven style artifact with well understood versioning.*/
case class Artifact(group: String, name: String, version: Version) {
  /** Produces sbt style formatting of the artifact.*/
  def pretty = s"$group % $name % ${version.pretty}"

  /** Convert to a generic artifact by changing the version to well formatted string.*/
  def toGeneric: GenericArtifact = GenericArtifact(group, name, version.pretty)
}

/** Artifact companion object.*/
object Artifact {
  /** Implicit conversion to a generic artifact by changing the version to well formatted string.*/
  implicit def ArtifactToGenericArtifact(a: Artifact): GenericArtifact =
    a.toGeneric
}

/** Maven style artifact where the version is just a string.*/
case class GenericArtifact(group: String, name: String, version: String) {
  def pretty = s"$group % $name % $version"
}
