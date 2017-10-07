import Dependencies._

lazy val `fs2-aws` = project
  .enablePlugins(GitPlugin, GitVersioning)
  .in(file("."))
  .settings(moduleName := "fs2-aws", name := "fs2-aws")
  .settings(noPublishSettings)
  .settings(gitSettings)
  .aggregate(
    auth,
    s3
  )

lazy val auth = project
  .enablePlugins(GitPlugin, GitVersioning)
  .in(file("modules/auth"))
  .settings(moduleName := "fs2-aws-auth", name := "fs2-aws-auth")
  .settings(scalaSettings)
  .settings(testSettings)
  .settings(commonDependenciesSettings)
  .settings(gitSettings)
  .settings(scalaFmtSettings)

lazy val s3 = project
  .dependsOn(auth)
  .enablePlugins(GitPlugin, GitVersioning)
  .in(file("modules/s3"))
  .settings(moduleName := "fs2-aws-s3", name := "fs2-aws-s3")
  .settings(scalaSettings)
  .settings(testSettings)
  .settings(commonDependenciesSettings)
  .settings(gitSettings)
  .settings(scalaFmtSettings)


lazy val dynamodb = project
  .enablePlugins(GitPlugin, GitVersioning)
  .in(file("modules/dynamodb"))
  .settings(moduleName := "fs2-aws-dynamodb", name := "fs2-aws-dynamodb")
  .settings(scalaSettings)
  .settings(testSettings)
  .settings(commonDependenciesSettings)
  .settings(gitSettings)
  .settings(scalaFmtSettings)


lazy val scalaSettings = Seq(
  scalaVersion := "2.12.3",
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-unchecked",
    "-Xfatal-warnings",
    "-Xlint",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xfuture",
    "-Ywarn-unused-import",
    "-Ywarn-unused"
  ),
  scalacOptions in (Compile, console) --= Seq("-Xlint", "-Ywarn-unused", "-Ywarn-unused-import"),
  scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value
)

lazy val metadataSettings = Seq(
  name := "fs2-aws",
  organization := "com.github.filosganga",
  organizationName := "Filippo De Luca",
  organizationHomepage := Some(url("https://github.com/filosganga"))
)

lazy val noPublishSettings =
  metadataSettings ++ Seq(
    publish := (),
    publishLocal := (),
    publishArtifact := false
  )

lazy val testSettings = Seq(
  logBuffered in Test := false,
  parallelExecution in Test := false,
  testOptions in Test += Tests.Argument("-oDF"),
  scalacOptions in Test --= Seq("-Xlint", "-Ywarn-unused", "-Ywarn-unused-import"),
  libraryDependencies ++= Seq(
    scalaTest % Test,
    scalaCheck % Test,
    logback.classic % Test
  )
)

lazy val commonDependenciesSettings = Seq(
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
  )
)

lazy val gitSettings = Seq(
  git.baseVersion := "0.1.0",
  git.useGitDescribe := true
)

lazy val scalaFmtSettings = Seq(
  scalafmtOnCompile := true
)