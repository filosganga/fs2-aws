import Dependencies._

lazy val `fs2-s3` = (project in file("."))
  .enablePlugins(GitPlugin, GitVersioning)
  .settings(
    git.baseVersion := "0.1.0",
    git.useGitDescribe := true,
    scalacOptions := Seq(
//      "-Ylog-classpath"
    )
  )
  .settings(
    inThisBuild(List(
      organization := "com.github.filosganga",
      scalaVersion := "2.12.3"
    )),
    name := "fs2-s3",
    libraryDependencies ++= Seq(
    	cats.kernel,
      cats.macros,
      cats.core,
      http4s.client,
      http4s.blazeClient,
      http4s.scalaXml,
      fs2.core,
      fs2.io,
      scalaModules.xml,
      "com.typesafe.scala-logging" % "scala-logging_2.12" % "3.7.2",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
    	scalaTest % Test,
      scalaCheck % Test
    )
  )
