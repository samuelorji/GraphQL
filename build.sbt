name := "GraphQL"

version := "0.1"

scalaVersion := "2.12.8"

val akkaVersion      = "2.5.19"
val akkaHttpVersion  = "10.1.7"

libraryDependencies ++= Seq(
  "com.typesafe.akka"      %% "akka-actor"           % akkaVersion,
  "com.typesafe.akka"      %% "akka-slf4j"           % akkaVersion,
  "com.typesafe.akka"      %% "akka-http"            % akkaHttpVersion,
  "com.typesafe.akka"      %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka"      %% "akka-stream"          % akkaVersion,
  "io.spray"               %% "spray-json"           % "1.3.3",
  "org.sangria-graphql"    %% "sangria"              % "1.4.2",
  "org.sangria-graphql"    %% "sangria-spray-json"   % "1.0.1"
)