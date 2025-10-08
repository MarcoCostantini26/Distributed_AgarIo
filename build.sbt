ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.6"
resolvers += "Akka library repository".at("https://repo.akka.io/maven")
lazy val akkaVersion = "2.10.5"
lazy val root = (project in file("."))
  .settings(
    name := "agar-io",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion, // For standard log configuration
      "com.typesafe.akka" %% "akka-remote" % akkaVersion, // For akka remote
      "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion, // akka clustering module
      "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
      "ch.qos.logback" % "logback-classic" % "1.5.18",
      "org.scala-lang.modules" %% "scala-swing" % "3.0.0",
      "com.typesafe.akka" %% "akka-actor-typed" % "2.8.8"
    )
  )
