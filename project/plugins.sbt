resolvers += Resolver.url("commbank-releases-ivy", new URL("http://commbank.artifactoryonline.com/commbank/ext-releases-local-ivy"))(Patterns("[organization]/[module]_[scalaVersion]_[sbtVersion]/[revision]/[artifact](-[classifier])-[revision].[ext]"))

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.11.1")

addSbtPlugin("au.com.cba.omnia" % "uniform-core" % "0.0.1-20140319230817-9bf2ec4")
