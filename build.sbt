name := """london-bus-tracker-play"""

version := "1.10"

scalaVersion := "2.11.7"

lazy val root = project.in(file(".")).enablePlugins(PlayScala)

//fork in run := true

libraryDependencies += "com.typesafe.play.plugins" %% "play-plugins-mailer" % "2.3.1"

libraryDependencies += "org.apache.httpcomponents" % "httpclient" % "4.5"

libraryDependencies += "org.apache.commons" % "commons-math3" % "3.5"

libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.4"

libraryDependencies += "org.mongodb" %% "casbah" % "3.1.0"

libraryDependencies += "org.json4s" %% "json4s-native" % "3.2.10"


//libraryDependencies += "org.reactivemongo" %% "play2-reactivemongo" % "0.11.2.play24"

//libraryDependencies += "org.atmosphere" % "atmosphere-runtime" % "2.1.0" withSources()

libraryDependencies += "org.atmosphere" % "atmosphere-play" % "2.1.2"

//libraryDependencies += "javax.servlet" % "javax.servlet-api" % "3.1.0" withSources()