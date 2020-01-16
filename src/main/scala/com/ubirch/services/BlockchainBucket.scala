package com.ubirch.services

import com.ubirch.kafka.express.ExpressKafkaApp
import org.apache.kafka.common.serialization.{ Deserializer, Serializer, StringDeserializer, StringSerializer }

object BlockchainBucket extends ExpressKafkaApp[String, String, Unit] {

  import com.ubirch.models.BlockchainSystem._
  import com.ubirch.models.BlockchainProcessors._

  override val keyDeserializer: Deserializer[String] = new StringDeserializer

  override val valueDeserializer: Deserializer[String] = new StringDeserializer

  override def consumerTopics: Set[String] = conf.getString("blockchainAnchoring.kafkaConsumer.topics").split(",").toSet.filter(_.nonEmpty)

  def producerTopics: Set[String] = conf.getString("blockchainAnchoring.kafkaProducer.topic").split(",").toSet.filter(_.nonEmpty)

  override def consumerBootstrapServers: String = conf.getString("blockchainAnchoring.kafkaConsumer.bootstrapServers")

  override def consumerGroupId: String = conf.getString("blockchainAnchoring.kafkaConsumer.topics")

  override def consumerMaxPollRecords: Int = conf.getInt("blockchainAnchoring.kafkaConsumer.maxPollRecords")

  override def consumerGracefulTimeout: Int = conf.getInt("blockchainAnchoring.kafkaConsumer.gracefulTimeout")

  override def producerBootstrapServers: String = conf.getString("blockchainAnchoring.kafkaProducer.bootstrapServers")

  override val keySerializer: Serializer[String] = new StringSerializer

  override val valueSerializer: Serializer[String] = new StringSerializer

  override def metricsSubNamespace: String = conf.getString("blockchainAnchoring.kafkaConsumer.metricsSubNamespace")

  override def consumerReconnectBackoffMsConfig: Long = conf.getLong("blockchainAnchoring.kafkaConsumer.reconnectBackoffMsConfig")

  override def consumerReconnectBackoffMaxMsConfig: Long = conf.getLong("blockchainAnchoring.kafkaConsumer.reconnectBackoffMaxMsConfig")

  override def lingerMs: Int = conf.getInt("blockchainAnchoring.kafkaProducer.lingerMS")

  val blockchainType: BlockchainType = BlockchainType.fromString(conf.getString("blockchainAnchoring.type")).getOrElse(throw new Exception("No Blockchain type set"))

  override def process: Process = Process { consumerRecords =>

    consumerRecords.foreach { x =>
      blockchainType match {
        case EthereumType =>
          val processed = EthereumBlockchain(Seq(Data(x.value()))).process
          println(processed)
        case EthereumClassicType => EthereumClassicBlockchain(Seq(Data(x.value()))).process
        case IOTAType => IOTABlockchain(Seq(Data(x.value()))).process
      }
    }

  }

}
