name := """PlaySilhouetteRest"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

resolvers += Resolver.jcenterRepo

// Silhouette config
val silhouetteVer = "4.0.0"
lazy val silhouetteLib = Seq(
  "com.mohiva" %% "play-silhouette" % silhouetteVer,
  "com.mohiva" %% "play-silhouette-password-bcrypt" % silhouetteVer,
  "com.mohiva" %% "play-silhouette-crypto-jca" % silhouetteVer,
  "com.mohiva" %% "play-silhouette-persistence" % silhouetteVer,
  "com.mohiva" %% "play-silhouette-testkit" % silhouetteVer % "test"
)

libraryDependencies ++= Seq(
  //jdbc,
  //cache,
  ws,
  filters,
  "net.codingwell" %% "scala-guice" % "4.1.0",
  "com.iheart" %% "ficus" % "1.2.6", // config lib, used by Silhouette,
  "org.reactivemongo" %% "play2-reactivemongo" % "0.11.14",
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test
) ++ silhouetteLib

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
