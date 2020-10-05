name := """datomisca-dao"""

version := "0.2.0"

lazy val module = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.3"

resolvers ++= Seq(
  //Resolver.bintrayRepo("dwhjames", "maven"),
  Resolver.bintrayRepo("thyming", "maven"),
  "clojars" at "https://clojars.org/repo",
  "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
)


libraryDependencies ++= Seq(
  //"com.github.dwhjames" %% "datomisca" % "0.7.0" % "provided",
  "com.github.enalmada" %% "datomisca" % "0.8.0" % "provided",
  "com.datomic" % "datomic-free" % "0.9.5544" % "provided",
  "org.specs2" %% "specs2-matcher-extra" % "4.8.1" % "test",
  "org.specs2" %% "specs2-junit" % "4.8.1" % "test",
  "org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0",
    specs2 % Test
)

scalacOptions in Test ++= Seq("-Yrangepos")

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator

// These tests depend on the order.  Should fix that so they are independant.
parallelExecution in Test := false


//*******************************
// Maven settings
//*******************************

publishMavenStyle := true

organization := "com.github.enalmada"

description := "Datomisca dao for easier crud."

startYear := Some(2016)

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra in Global := {
  <url>https://github.com/Enalmada/datomisca-dao</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <scm>
      <connection>scm:git:git@github.com:enalmada/datomisca-dao.git</connection>
      <developerConnection>scm:git:git@github.com:enalmada/datomisca-dao.git</developerConnection>
      <url>https://github.com/enalmada</url>
    </scm>
    <developers>
      <developer>
        <id>enalmada</id>
        <name>Adam Lane</name>
        <url>https://github.com/enalmada</url>
      </developer>
    </developers>
}

credentials += Credentials(Path.userHome / ".sbt" / "sonatype.credentials")

// https://github.com/xerial/sbt-sonatype/issues/30
sources in (Compile, doc) := Seq()

credentials += Credentials(
  "GnuPG Key ID",
  "gpg",
  "DC79DFF439FE3C2922DC880AC80D0C3CE5ED2C26", // key identifier
  "ignored" // this field is ignored; passwords are supplied by pinentry
)

publishConfiguration := publishConfiguration.value.withOverwrite(true)
publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)
