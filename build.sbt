name := "twitter-realtime-sentiment"

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies ++= {
  val akkaVersion = "2.3.12"
  val akkaStreamVersion = "1.0"

  Seq(
    "org.apache.spark" %% "spark-core" % "1.5.2" % "provided",
//    "org.apache.spark" %% "spark-sql" % "1.5.2" % "provided",
    "org.apache.spark" %% "spark-streaming" % "1.5.2" % "provided",
    "org.apache.spark" %% "spark-streaming-twitter" % "1.5.2",
//    "org.apache.spark" %% "spark-streaming-kafka" % "1.5.2",
    "com.datastax.spark" %% "spark-cassandra-connector" % "1.5.0-M2",
//    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
//    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream-experimental" % akkaStreamVersion,
    "com.typesafe.akka" %% "akka-http-core-experimental" % akkaStreamVersion,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaStreamVersion,
//    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaStreamVersion,
//    "com.typesafe.akka" %% "akka-http-xml-experimental" % akkaStreamVersion
    "org.json4s" %% "json4s-native" % "3.2.10",
    "org.json4s" %% "json4s-jackson" % "3.2.10"
  )
}

assemblyMergeStrategy in assembly := {

  case PathList("META-INF", "maven", xs @ _*) => MergeStrategy.discard
  case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.last
  case PathList("org", "apache", "spark", "unused", "UnusedStubClass.class") => MergeStrategy.first
//  case x => (assemblyMergeStrategy in assembly).value(x)
  case x =>
    val baseStrategy = (assemblyMergeStrategy in assembly).value
    baseStrategy(x)
}
