import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

    val appName         = "lobid"
    val appVersion      = com.typesafe.config.ConfigFactory.parseFile(new File("conf/application.conf")).resolve().getString("application.version")
    val appDependencies = Seq(
      javaCore,
      cache,
      "com.typesafe.play" % "play-test_2.10" % "2.2.2",
      "org.elasticsearch" % "elasticsearch" % "1.1.0" withSources(),
      "org.lobid" % "lodmill-ld" % "1.8.1",
      "org.scalatest" %% "scalatest" % "1.9.1" % "test"
    )

    val main = play.Project(appName, appVersion, appDependencies).settings(
      parallelExecution in Test := false,
      resolvers := Seq(
          "codehaus" at "http://repository.codehaus.org/org/codehaus", 
          "typesafe" at "http://repo.typesafe.com/typesafe/repo", 
          "jena-dev" at "https://repository.apache.org/content/repositories/snapshots",
          Resolver.mavenLocal)
    )

    val javacOptions = Seq("-source", "1.8", "-target", "1.8")
}
