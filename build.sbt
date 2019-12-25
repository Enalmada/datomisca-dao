import sbt.Keys._
import sbt.Project.projectToRef
import sbt._

name := """datomisca-dao-sample"""

version := "1.0-SNAPSHOT"

lazy val module = (project in file("module")).enablePlugins(PlayScala)

lazy val root = (project in file(".")).enablePlugins(PlayScala).aggregate(module).dependsOn(module)

scalaVersion := "2.12.10"

resolvers ++= Seq(
  Resolver.bintrayRepo("thyming", "maven"),
  "clojars" at "https://clojars.org/repo",
  "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
)

libraryDependencies ++= Seq(
  ws,
  "com.quartethealth" %% "datomisca" % "0.7.1",
  "com.datomic" % "datomic-free" % "0.9.5544",
  "org.webjars" %% "webjars-play" % "2.6.0-M1",
  "org.webjars" % "bootswatch-superhero" % "4.2.1", // Bootstrap and jquery come with it
  "com.adrianhurt" %% "play-bootstrap" % "1.5.1-P27-B4" exclude("org.webjars", "jquery") exclude("org.webjars", "bootstrap") // Bootstrap forms wrapper (Bootstrap and jquery included)
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
