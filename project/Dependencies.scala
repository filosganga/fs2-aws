import sbt._


object Dependencies {

  object cats {
    private val version = "1.0.0-MF"

    val macros = "org.typelevel" %% "cats-macros" % version
    val kernel = "org.typelevel" %% "cats-kernel" % version
    val core = "org.typelevel" %% "cats-core" % version
    val free = "org.typelevel" %% "cats-free" % version
  }

  object http4s {
    private val version = "0.18.0-M1"

    val client = "org.http4s" %% "http4s-client" % version
    val blazeClient = "org.http4s" %% "http4s-blaze-client" % version
    val circe = "org.http4s" %% "http4s-circe" % version
    val scalaXml = "org.http4s" %% "http4s-scala-xml" % version
  }

  object fs2 {
    private val version = "0.10.0-M6"

    val core = "co.fs2" %% "fs2-core" % version
    val io = "co.fs2" %% "fs2-io" % version
  }

  object circe {

    private val version = "0.8.0"

    val core = "io.circe" %% "circe-core" % version
    val generic = "io.circe" %% "circe-generic" % version
    val parser = "io.circe" %% "circe-parser" % version
  }

  object scalaModules {
    val xml = "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
  }

  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.4"
  val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.13.5"
  val log4s = "org.log4s" %% "log4s" % "1.4.0"

  object logback {
    val classic = "ch.qos.logback" % "logback-classic" % "1.2.3"
  }

}
