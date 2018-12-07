//   Copyright 2014-2018 Commonwealth Bank of Australia
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

import AssemblyKeys._

name := "wrangler"

scalaVersion := "2.10.7"

crossScalaVersions := Seq("2.10.3", "2.10.4", "2.10.5")

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
  "org.scala-sbt"            % "io"                                % "0.13.15",
  "com.typesafe"             % "config"                            % "1.2.1",
  "com.quantifind"          %% "sumac"                             % "0.3.0",
  "com.quantifind"          %% "sumac-ext"                         % "0.3.0",
  "org.slf4j"                % "slf4j-api"                         % "1.7.6",
  "ch.qos.logback"           % "logback-core"                      % "1.1.1",
  "ch.qos.logback"           % "logback-classic"                   % "1.1.1"
)

mergeStrategy in assembly := {
  case "META-INF/NOTICE.txt" => MergeStrategy.rename
  case "META-INF/LICENSE.txt" => MergeStrategy.rename
  case "META-INF/MANIFEST.MF" => MergeStrategy.discard
  case PathList("META-INF", xs) if xs.toLowerCase.endsWith(".dsa") => MergeStrategy.discard
  case PathList("META-INF", xs) if xs.toLowerCase.endsWith(".rsa") => MergeStrategy.discard
  case PathList("META-INF", xs) if xs.toLowerCase.endsWith(".sf") => MergeStrategy.discard
  case _ => MergeStrategy.first
}
