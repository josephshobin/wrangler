package wrangler.data

case class Artifact(group: String, name: String, version: Version) {
  def pretty = s"$group % $name % ${version.pretty}"

  def toGeneric: GenericArtifact = GenericArtifact(group, name, version.pretty)
}

object Artifact {
  implicit def ArtifactToGenericArtifact(a: Artifact): GenericArtifact =
    a.toGeneric
}

case class GenericArtifact(group: String, name: String, version: String) {
  def pretty = s"$group % $name % $version"
}
