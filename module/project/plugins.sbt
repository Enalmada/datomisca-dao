import sbt.VersionScheme

resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

// For sbt 1.1.x, and 0.13.x
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.6")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.2")

// http://www.onegeek.com.au/scala/setting-up-travis-ci-for-scala
/*
resolvers += Resolver.url(
  "bintray-sbt-plugin-releases",
  url("http://dl.bintray.com/content/sbt/sbt-plugin-releases"))(
  Resolver.ivyStylePatterns)

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.1.1")

resolvers += Classpaths.sbtPluginReleases

resolvers += Classpaths.typesafeReleases

addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "0.99.5.1")

addSbtPlugin("com.sksamuel.scoverage" %% "sbt-coveralls" % "0.0.5")

// Add the following to have Git manage your build versions
resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"

addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.6.4")
*/

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.6.3")

// https://github.com/playframework/playframework/releases/tag/2.8.19
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
