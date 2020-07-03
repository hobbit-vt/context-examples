name := "context-examples"

version := "0.1"

scalaVersion := "2.13.1"

//addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)
addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full)

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % "2.0.0",
  "org.typelevel" %% "cats-effect" % "2.1.2",
  "dev.zio" %% "zio" % "1.0.0-RC21-2",
  "com.twitter" %% "util-core" % "20.6.0",
  "io.monix" %% "monix" % "3.2.2"
)

scalacOptions ++= Seq(
  "-Ymacro-annotations"
)

