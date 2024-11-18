// build.sbt
name := "datomisca-dao"

version := "0.2.5"

lazy val module = (project in file("."))

scalaVersion := "2.13.15"

// Cross version configuration
crossScalaVersions := Seq("2.12.20", "2.13.15")

unmanagedSourceDirectories in Compile += (sourceDirectory in Compile).value / s"scala_${scalaBinaryVersion.value}"

resolvers ++= Seq(
  Resolver.bintrayRepo("thyming", "maven"),
  "clojars" at "https://clojars.org/repo",
  "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
)

libraryDependencies ++= {
  val commonDeps = Seq(
    "com.github.enalmada" %% "datomisca" % "0.8.5" % "provided",
    "com.datomic" % "peer" % "1.0.7260" % "provided",
    "org.specs2" %% "specs2-matcher-extra" % "4.8.1" % Test,
    "org.specs2" %% "specs2-junit" % "4.8.1" % Test,
    "org.slf4j" % "slf4j-api" % "2.0.7",
    "ch.qos.logback" % "logback-classic" % "1.4.11"
    // specs2 % Test -- looks like specs2 was added twice; verify if still needed
  )

  val scalaParallelCollections = if (scalaVersion.value.startsWith("2.13")) {
    Seq(
    "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4",
    "org.playframework" %% "play-json" % "3.0.4")
  } else {
    Seq("org.playframework" %% "play-json" % "3.0.4")
  }

  commonDeps ++ scalaParallelCollections
}

Test / scalacOptions ++= Seq("-Yrangepos")

// Test execution configuration
Test / parallelExecution := false

//**********
// Maven settings
//**********

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

Test / publishArtifact := false

pomIncludeRepository := { _ => false }

Global / pomExtra := {
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

// Important GPG issues
Compile / doc / sources := Seq()

credentials += Credentials(
  "GnuPG Key ID",
  "gpg",
  "DC79DFF439FE3C2922DC880AC80D0C3CE5ED2C26",
  "ignored"
)

publishConfiguration := publishConfiguration.value.withOverwrite(true)
publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)
