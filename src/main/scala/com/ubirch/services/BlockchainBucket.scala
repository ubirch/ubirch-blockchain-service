package com.ubirch.services

import com.ubirch.kafka.express.{ ConfigBase, ExpressKafkaApp }
import com.ubirch.models.{ BlockchainJsonSupport, Response }
import org.apache.kafka.common.serialization.{ Deserializer, Serializer, StringDeserializer, StringSerializer }

trait BlockchainProcessorConnector extends ConfigBase {
  a: ExpressKafkaApp[String, String, Unit] =>

  import com.ubirch.models.BlockchainProcessors._
  import com.ubirch.models.BlockchainSystem._

  lazy val producerTopics: Set[String] = conf.getString("blockchainAnchoring.kafkaProducer.topic").split(",").toSet.filter(_.nonEmpty)
  val blockchainType: BlockchainType = BlockchainType.fromString(conf.getString("blockchainAnchoring.type")).getOrElse(throw new Exception("No Blockchain type set"))

  logger.info("Configured blockchain={}", blockchainType.value)

  override val process: Process = Process { consumerRecords =>

    consumerRecords.foreach { cr =>

      val response = blockchainType match {
        case EthereumType => EthereumBlockchain(Seq(Data(cr.value()))).process
        case EthereumClassicType => EthereumClassicBlockchain(Seq(Data(cr.value()))).process
        case IOTAType => IOTABlockchain(Seq(Data(cr.value()))).process
      }

      response match {
        case Left(Some(value)) => producerTopics.map(topic => send(topic, BlockchainJsonSupport.ToJson[Response](value).toString()))
        case Left(None) =>
        case Right(exception) => throw exception
      }

    }

  }

}

trait BlockchainBucketBase extends ExpressKafkaApp[String, String, Unit] {

  override val keyDeserializer: Deserializer[String] = new StringDeserializer
  override val valueDeserializer: Deserializer[String] = new StringDeserializer
  override val consumerTopics: Set[String] = conf.getString("blockchainAnchoring.kafkaConsumer.topics").split(",").toSet.filter(_.nonEmpty)
  override val keySerializer: Serializer[String] = new StringSerializer
  override val valueSerializer: Serializer[String] = new StringSerializer
  override val consumerBootstrapServers: String = conf.getString("blockchainAnchoring.kafkaConsumer.bootstrapServers")
  override val consumerGroupId: String = conf.getString("blockchainAnchoring.kafkaConsumer.topics")
  override val consumerMaxPollRecords: Int = conf.getInt("blockchainAnchoring.kafkaConsumer.maxPollRecords")
  override val consumerGracefulTimeout: Int = conf.getInt("blockchainAnchoring.kafkaConsumer.gracefulTimeout")
  override val producerBootstrapServers: String = conf.getString("blockchainAnchoring.kafkaProducer.bootstrapServers")
  override val metricsSubNamespace: String = conf.getString("blockchainAnchoring.kafkaConsumer.metricsSubNamespace")
  override val consumerReconnectBackoffMsConfig: Long = conf.getLong("blockchainAnchoring.kafkaConsumer.reconnectBackoffMsConfig")
  override val consumerReconnectBackoffMaxMsConfig: Long = conf.getLong("blockchainAnchoring.kafkaConsumer.reconnectBackoffMaxMsConfig")
  override val lingerMs: Int = conf.getInt("blockchainAnchoring.kafkaProducer.lingerMS")

}
