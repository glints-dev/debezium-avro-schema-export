import ReleaseTransformations._

scalaVersion := "2.13.7"
version := "0.1.0-SNAPSHOT"

val confluentVersion = "6.2.2"
val debeziumVersion = "1.7.2.Final"
val kafkaVersion = "2.8.1"

resolvers += "Confluent Repository" at "https://packages.confluent.io/maven/"

libraryDependencies += "io.confluent" % "kafka-connect-avro-converter" % confluentVersion
libraryDependencies += "io.confluent" % "kafka-connect-avro-data" % confluentVersion
libraryDependencies += "io.debezium" % "debezium-core" % debeziumVersion
libraryDependencies += "io.debezium" % "debezium-connector-postgres" % debeziumVersion
libraryDependencies += "org.apache.kafka" % "connect-api" % kafkaVersion
libraryDependencies += "org.slf4j" % "slf4j-nop" % "1.7.32"

assemblyMergeStrategy := {
    case PathList("local.properties") => MergeStrategy.discard
    case PathList("module-info.class") => MergeStrategy.discard
    case x =>
        val oldStrategy = assemblyMergeStrategy.value
        oldStrategy(x)
}

releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    setNextVersion,
    commitNextVersion,
    pushChanges,
)
