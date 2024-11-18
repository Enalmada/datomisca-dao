import sbt.Keys._
import sbt._

name := """datomisca-dao-sample"""

version := "1.1-SNAPSHOT"

lazy val module = (project in file("module")).enablePlugins(PlayScala)

lazy val root = (project in file(".")).enablePlugins(PlayScala).aggregate(module).dependsOn(module)

scalaVersion := "2.13.15"

resolvers ++= Seq(
  Resolver.bintrayRepo("thyming", "maven"),
  "clojars" at "https://clojars.org/repo",
  "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
)

libraryDependencies ++= Seq(
  ws,
  "com.github.enalmada" %% "datomisca" % "0.8.5",
  "com.datomic" % "peer" % "1.0.7260",
  "org.webjars" %% "webjars-play" % "3.0.2",
  "org.webjars" % "bootswatch-superhero" % "5.0.2", // Bootstrap and jquery come with it
  "com.adrianhurt" %% "play-bootstrap" % "1.6.1-P28-B4" exclude("org.webjars", "jquery") exclude("org.webjars", "bootstrap") // Bootstrap forms wrapper (Bootstrap and jquery included)
)

scalacOptions in Test ++= Seq("-Yrangepos")

libraryDependencies += specs2

includeFilter in digest := "*.js" | "*.css"

pipelineStages := Seq(uglify, digest, gzip)

includeFilter in(Assets, StylusKeys.stylus) := "main.styl" | "adminMain.styl"

StylusKeys.useNib in Assets := true

StylusKeys.compress in Assets := true



// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator
