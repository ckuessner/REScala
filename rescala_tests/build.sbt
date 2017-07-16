name := "rescala_tests"

version := "1.0"

scalaVersion := "2.12.1"

resolvers += Resolver.bintrayRepo("rmgk", "maven")
resolvers += Resolver.bintrayRepo("pweisenburger", "maven")

libraryDependencies += "de.tuda.stg" %% "rescala" % "0.19.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.3",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.3" % Test
)