import sbt._
import Keys._
import sbtrelease.ReleasePlugin.autoImport._

object Versions {

  val dropwizard = "0.8.5"
  val jackson = "2.5.1"

  val mockito = "1.10.19"

  val project  = Versions.dropwizard + "-1-SNAPSHOT"
}

case class Versions(scalaBinaryVersion: String) {

  val dropwizard = Versions.dropwizard
  val jackson = Versions.jackson

  val mockito = Versions.mockito
  val scalaTest = scalaBinaryVersion match {
    case "2.10" | "2.11" => "2.2.6"
  }
}

case class Dependencies(scalaBinaryVersion: String) {

  private val versions = Versions(scalaBinaryVersion)

  val compile = scalaBinaryVersion match {
    // note: scala-logging is only available for Scala 2.10+
    case "2.10" | "2.11" => Seq("com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2")
    case _ => Seq.empty
  }

  val test = Seq(
    "org.scalatest" %% "scalatest" % versions.scalaTest % "test",
    "org.mockito" % "mockito-core" % versions.mockito % "test"
  )
}

object CompileOptions {

  def scala(scalaVersion: String): Seq[String] =
    "-deprecation" ::
      "-unchecked" ::
      "-language:implicitConversions" ::
      "-language:higherKinds" ::
      "-feature" ::
      (scalaVersion match {
        case v if v.startsWith("2.11.") && v.stripSuffix("2.11.").toInt > 4 =>
          "-target:jvm-1.8"
        case _ =>
          "-target:jvm-1.7"
      }) ::
      Nil

  val java: Seq[String] = Seq("-source", "1.8", "-target", "1.8")
}

object DropwizardScala extends Build {

  val buildSettings = super.settings ++ Seq(
    description := "Scala language integration for the Dropwizard project.",
    homepage := Option(url("http://github.com/datasift/dropwizard-scala")),
    startYear := Option(2014),
    licenses += ("Apache License 2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")),
    organization := "com.datasift.dropwizard.scala",
    scmInfo := Option(ScmInfo(
      browseUrl = url("http://github.com/dropwizard/dropwizard-scala/"),
      connection = "git://github.com/dropwizard/dropwizard-scala.git",
      devConnection = Option("git@github.com@:dropwizard/dropwizard-scala.git")
    )),
    crossScalaVersions := Seq("2.10.6", "2.11.8"),
    scalacOptions <++= scalaVersion.map(CompileOptions.scala),
    javacOptions ++= CompileOptions.java,
    resolvers in ThisBuild ++= Seq(
      "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    ),
    libraryDependencies <++= scalaBinaryVersion(Dependencies).apply(_.compile),
    libraryDependencies <++= scalaBinaryVersion(Dependencies).apply(_.test),
    publishMavenStyle := true,
    publishTo <<= isSnapshot(repository),
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    pomExtra := {
      <developers>
        <developer>
          <id>nicktelford</id>
          <name>Nick Telford</name>
          <email>nick.telford@gmail.com</email>
        </developer>
      </developers>
    },
    unmanagedSourceDirectories in Compile <++= (sourceDirectory in Compile, scalaBinaryVersion) {
      case (s, v) => s / ("scala_" + v) :: Nil
    },
    version := Versions.project,
    releaseTagName <<= (name, version in ThisBuild) map { (n,v) => n + "-" + v }
  )

  def module(id: String): sbt.Project = module(id, file(id), Nil)
  def module(id: String, file: sbt.File, settings: Seq[Def.Setting[_]]): sbt.Project = {
    Project(id, file, settings = buildSettings ++ settings ++ Seq(
      name := "Dropwizard Scala %s".format(id),
      normalizedName := "dropwizard-scala-%s".format(id)
    ))
  }

  def repository(isSnapshot: Boolean) = {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }

  lazy val core = module("core").dependsOn(jersey, validation)
  lazy val jersey = module("jersey")
  lazy val validation = module("validation")
  lazy val jdbi = module("jdbi")
  lazy val metrics = module("metrics")

  lazy val parent = module("parent", file("."), Seq(
      publishArtifact := false,
      Keys.`package` := file(""),
      packageBin in Global := file(""),
      packagedArtifacts := Map()))
    .aggregate(core, jersey, jdbi, validation, metrics)
}
