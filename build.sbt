import sbt.Keys.libraryDependencies

val compilerOptions = Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-deprecation",
    "-language:implicitConversions",
    "-unchecked",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Xlint",
    "-language:_",
    "-Ypartial-unification",
    "-Xfatal-warnings"
)


val dependencies = Seq(
    "org.scalaz" %% "scalaz-zio" % "1.0-RC5",
    "org.scalaz" %% "scalaz-zio-interop" % "0.5.0",
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "org.scodec" %% "scodec-core" % "1.11.3"
).map(libraryDependencies += _)

val testDependencies = Seq(
    "org.scalatest" %% "scalatest" % "3.0.5",
).map(libraryDependencies += _ % Test)

val teleserv = project.in(file("."))
  .settings(
    scalaVersion := "2.12.7",
    dependencies,
    testDependencies,
    scalacOptions := compilerOptions
  )

