import sbt._

object Dependencies {

  object V {
    val akka = "2.3.3"
    val scalaz = "7.1.0-M7"
  }

  object CompileDependencies {

    val akkaActor = "com.typesafe.akka" %% "akka-actor" % V.akka

    val akkaAgent = "com.typesafe.akka" %% "akka-agent" % V.akka

    val akkaRemote = "com.typesafe.akka" %% "akka-remote" % V.akka

    val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % V.akka

    val akkaKernel = "com.typesafe.akka" %% "akka-kernel" % V.akka

    val logging = "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.0.4"

    val scalazCore = "org.scalaz" %% "scalaz-core" % V.scalaz

    val scalazConcurrent = "org.scalaz" %% "scalaz-concurrent" % V.scalaz

    val redis = "com.etaty.rediscala" %% "rediscala" % "1.3.1"

    val logback = "ch.qos.logback" % "logback-classic" % "1.1.2"

    val scodec = "org.typelevel" %% "scodec-core" % "1.0.0" // TODO: 1.1.0-SNAPSHOT

//    val scalaUtils = "org.scalautils" %% "scalautils" % "2.1.3"
//
//    val async = "org.scala-lang.modules" %% "scala-async" % "0.9.1"
//
//    val nscalaTime = "com.github.nscala-time" %% "nscala-time" % "1.0.0"
//
//    val macrodebug = "com.softwaremill.scalamacrodebug" %% "macros" % "0.4"
//
//    val macwiew = "com.softwaremill.macwire" %% "macros" % "0.6"
//
//    val shepless = "com.chuusai" %% "shapeless" % "2.0.0"
//
//    val optional = "org.nalloc" %% "optional" % "0.1.0"
//
//    val platformEx = "com.nocandysw" %% "platform-executing" % "0.5.0"
//
//    val stateless = "com.qifun" %% "stateless-future" % "0.1.1"
//
//    val faststring = "com.dongxiguo" %% "fastring" % "0.2.4"
//
//    val applyBuilder = "com.github.xuwei-k" %% "applybuilder70" % "0.1.2"
//
//    val nobox = "com.github.xuwei-k" %% "nobox" % "0.1.9"
//
//    val stm = "org.scala-stm" %% "scala-stm" % "0.7"
//
//    val parboiled = "org.parboiled" %% "parboiled-scala" % "1.1.6"
//
//    val scalaEquals = "org.scalaequals" %% "scalaequals-core" % "1.2.0"
//
//    val monocle = "com.github.julien-truffaut" %% "monocle-core" % "0.3.0"
//
//    val scalacache = "com.github.cb372" %% "scalacache-guava" % "0.3.0"

  }

  object DeployDependencies {

    val atmos = "com.typesafe.atmos" % "trace-akka-2.2.1_2.11.0-M3" % "1.3.1"

  }

  object TestDependencies {
    val scalatest = "org.scalatest" %% "scalatest" % "2.1.3" % "test"

    val akkaTest = "com.typesafe.akka" %% "akka-testkit" % V.akka % "test"

//    val scalaMockTest = "org.scalamock" %% "scalamock-scalatest-support" % "3.0.1" % "test"

    val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.11.3" % "test"
  }


  import CompileDependencies._
  import DeployDependencies._
  import TestDependencies._

  val akka = Seq(akkaKernel, akkaActor, akkaAgent, akkaRemote, akkaSlf4j, logback)

  val scalaz = Seq(scalazCore, scalazConcurrent)

  val dbs = Seq(redis)

  val etc = Seq(scodec)

  val testDependencies = Seq(scalatest, akkaTest) // , scalaMockTest)

}