import sbt.Keys._
import sbt.Project.projectToRef

name := """datomisca-dao-sample"""

version := "1.0-SNAPSHOT"

lazy val module = (project in file("module")).enablePlugins(PlayScala)

lazy val root = (project in file(".")).enablePlugins(PlayScala).aggregate(module).dependsOn(module)


scalaVersion := "2.11.8"

resolvers ++= Seq(
  Resolver.bintrayRepo("dwhjames", "maven"),
  "clojars" at "https://clojars.org/repo",
  "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
)

libraryDependencies ++= Seq(
  ws,
  "com.github.dwhjames" %% "datomisca" % "0.7.0",
  "com.datomic" % "datomic-free" % "0.9.5344",
  "org.webjars" %% "webjars-play" % "2.4.0-1",
  "org.webjars" % "bootswatch-superhero" % "3.3.5", // Bootstrap and jquery come with it
  "org.webjars" % "bootswatch-spacelab" % "3.3.5", // Bootstrap and jquery come with it
  "com.adrianhurt" %% "play-bootstrap3" % "0.4.4-P24", // Bootstrap forms wrapper (Bootstrap and jquery included)
  "org.mindrot" % "jbcrypt" % "0.3m", // Authentication - password encryption
  "jp.t2v" %% "play2-auth" % "0.14.2", // auth framework
  "jp.t2v" %% "play2-auth-social" % "0.14.2", // auth framework
  "jp.t2v" %% "play2-auth-test" % "0.14.2" % "test", // auth framework
  play.sbt.Play.autoImport.cache, // play2-auth - only when you use default IdContainer
  "com.typesafe.play" %% "play-mailer" % "5.0.0-M1" // Send mail smtp
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
