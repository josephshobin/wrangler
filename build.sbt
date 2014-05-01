import AssemblyKeys._

name := "wrangler"

version := "0.9.0"

scalaVersion := "2.10.4"

crossScalaVersions := Seq("2.10.3")

mainClass := Some("wrangler.Console")

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-Ywarn-all",
  "-Xlint",
  "-feature",
  "-language:_"
)

assemblySettings

libraryDependencies ++= Seq(
  "org.eclipse.jgit"         % "org.eclipse.jgit"                  % "3.3.0.201403021825-r",
  "org.scalaz"              %% "scalaz-core"                       % "7.0.6",
  "net.databinder.dispatch" %% "dispatch-core"                     % "0.11.0",
  "net.databinder.dispatch" %% "dispatch-json4s-native"            % "0.11.0",
  "com.jcraft"               % "jsch.agentproxy.jsch"              % "0.0.7",
  "com.jcraft"               % "jsch.agentproxy.connector-factory" % "0.0.7",
  "org.apache.karaf.shell"   % "org.apache.karaf.shell.console"    % "2.3.3",
  "org.slf4j"                % "slf4j-api"                         % "1.7.6",
  "ch.qos.logback"           % "logback-core"                      % "1.1.1",
  "ch.qos.logback"           % "logback-classic"                   % "1.1.1"
)

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old: (String => MergeStrategy)) => (path: String) => path match {
    case "META-INF/NOTICE.txt" => MergeStrategy.rename
    case "META-INF/LICENSE.txt" => MergeStrategy.rename
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case PathList("META-INF", xs) if xs.toLowerCase.endsWith(".dsa") => MergeStrategy.discard
    case PathList("META-INF", xs) if xs.toLowerCase.endsWith(".rsa") => MergeStrategy.discard
    case PathList("META-INF", xs) if xs.toLowerCase.endsWith(".sf") => MergeStrategy.discard
    case _ => MergeStrategy.first
  }
}
