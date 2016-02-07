name := """london-bus-tracker-play"""

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.7"

lazy val root = project.in(file(".")).enablePlugins(PlayScala)


fork in run := true

//libraryDependencies += "com.typesafe.scala-logging" % "scala-logging_2.11" % "3.1.0"

//libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.2"

libraryDependencies += "com.typesafe.play.plugins" %% "play-plugins-mailer" % "2.3.1"

libraryDependencies += "org.apache.httpcomponents" % "httpclient" % "4.5"

libraryDependencies += "org.apache.commons" % "commons-math3" % "3.5"

libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.4"

libraryDependencies += "org.mongodb" %% "casbah" % "2.8.2"

libraryDependencies += "org.json4s" %% "json4s-native" % "3.2.10"