package io.debezium.connector.postgresql

import java.io.FileNotFoundException
import java.net.URL
import java.util.Properties
import scala.io.Source.fromURL
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

import io.confluent.connect.avro.{AvroData, AvroDataConfig};
import io.debezium.config.Configuration
import io.debezium.connector.postgresql.connection.PostgresConnection
import io.debezium.connector.postgresql.connection.PostgresConnection.PostgresValueConverterBuilder
import java.io.FileInputStream

object DebeziumAvroSchemaExport {
  def main(args: Array[String]) = {
    val options = getOptions(args)
    val propFilePath: Option[String] = options.get('config) map { value =>
      value.toString
    }

    val config = getConfig(propFilePath)
    val connectorConfig = new PostgresConnectorConfig(config)
    val topicSelector = PostgresTopicSelector.create(connectorConfig)

    val heartbeatConnection = new PostgresConnection(
      connectorConfig.getJdbcConfig()
    )
    val databaseCharset = heartbeatConnection.getDatabaseCharset()

    val valueConverterBuilder: PostgresValueConverterBuilder =
      (typeRegistry: TypeRegistry) =>
        PostgresValueConverter.of(
          connectorConfig,
          databaseCharset,
          typeRegistry
        )

    val jdbcConnection = new PostgresConnection(
      connectorConfig.getJdbcConfig(),
      valueConverterBuilder
    )
    jdbcConnection.setAutoCommit(false)

    val typeRegistry = jdbcConnection.getTypeRegistry()
    val schema = new PostgresSchema(
      connectorConfig,
      typeRegistry,
      topicSelector,
      valueConverterBuilder.build(typeRegistry)
    )

    schema.refresh(jdbcConnection, false)

    val avroData = new AvroData(new AvroDataConfig(config.asMap()))

    for (tid <- schema.tableIds().asScala) {
      val avroSchema =
        avroData.fromConnectSchema(schema.schemaFor(tid).valueSchema())
      println(avroSchema.toString(false))
    }
  }

  private def getOptions(args: Array[String]): Map[Symbol, Any] = {
    type OptionMap = Map[Symbol, Any]

    def optionMap(map: OptionMap, list: List[String]): OptionMap = {
      def isSwitch(s: String) = (s(0) == '-')
      list match {
        case Nil => map
        case "--config" :: string :: tail =>
          optionMap(map ++ Map('config -> string), tail)
        case string :: opt2 :: tail if isSwitch(opt2) =>
          optionMap(map ++ Map('infile -> string), list.tail)
        case option :: tail =>
          println("Unknown option " + option)
          println("Usage: [--config local.properties]")
          sys.exit(1)
      }
    }

    optionMap(Map(), args.toList)
  }

  private def getConfig(propFilePath: Option[String]): Configuration = {
    val props = System.getProperties()

    getClass().getResource("/config.properties") match {
      case stream: URL => props.load(fromURL(stream).bufferedReader())
      case _ => throw new FileNotFoundException("config.properties not found")
    }

    getClass().getResource("/local.properties") match {
      case stream: URL => props.load(fromURL(stream).bufferedReader())
      case _           =>
    }

    propFilePath match {
      case Some(value) =>
        Try { new FileInputStream(value) } match {
          case Success(stream) => props.load(stream)
          case Failure(stream) =>
            throw new FileNotFoundException(s"$propFilePath not found")
        }
      case None =>
    }

    props.setProperty("name", "debezium-avro-schema-export")
    props.setProperty(
      "connector.class",
      "io.debezium.connector.postgresql.PostgresConnector"
    )

    props.setProperty(
      "key.converter",
      "io.confluent.connect.avro.AvroConverter"
    )
    props.setProperty(
      "value.converter",
      "io.confluent.connect.avro.AvroConverter"
    )

    Configuration.from(props)
  }
}
