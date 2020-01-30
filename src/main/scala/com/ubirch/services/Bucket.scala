package com.ubirch.services

import com.ubirch.kafka.express.{ ConfigBase, ExpressKafkaApp }
import com.ubirch.models.{ Response, TransactionMetrics }
import com.ubirch.util.JsonSupport
import org.apache.kafka.common.serialization.{ Deserializer, Serializer, StringDeserializer, StringSerializer }

trait BucketPicker extends TransactionMetrics with ConfigBase {
  a: ExpressKafkaApp[String, String, Unit] =>

  import com.ubirch.models.BlockchainProcessors._
  import com.ubirch.models.BlockchainSystem._

  val producerTopics: Set[String] = conf.getString("blockchainAnchoring.kafkaProducer.topics").split(",").toSet.filter(_.nonEmpty)
  final val blockchainType: BlockchainType = BlockchainType.fromString(conf.getString("blockchainAnchoring.type")).getOrElse(throw new Exception("No Blockchain type set"))
  final val flush: Boolean = conf.getBoolean("flush")

  logger.info("Configured blockchain={}", blockchainType.value)

  //We have this dummy call in order to boost things up, like getting the current balance.
  sendData(Nil)

  override val process: Process = Process { consumerRecords =>

    if (!flush) {
      val data = consumerRecords.map(x => Data(x.value()))

      sendData(data) match {
        case Left(responses) =>
          if (responses.isEmpty) {
            errorCounter.labels(blockchainType.value).inc()
            //No need to react to this response as this type of response is intended to be a not critical blockchain exception/error, with is
            //totally OK to just let go and continue with other values.
          } else {
            successCounter.labels(blockchainType.value).inc()
            responses.map { res =>
              producerTopics.map(topic => send(topic, JsonSupport.ToJson[Response](res).toString()))
            }
          }
        case Right(exception) =>
          errorCounter.labels(blockchainType.value).inc()
          throw exception
      }
    }

  }

  def sendData(data: Seq[Data]) =
    blockchainType match {
      case EthereumType => EthereumBlockchain(data).process
      case EthereumClassicType => EthereumClassicBlockchain(data).process
      case IOTAType => IOTABlockchain(data).process
    }

}

trait Bucket extends ExpressKafkaApp[String, String, Unit] {

  override val keyDeserializer: Deserializer[String] = new StringDeserializer
  override val valueDeserializer: Deserializer[String] = new StringDeserializer
  override val consumerTopics: Set[String] = conf.getString("blockchainAnchoring.kafkaConsumer.topics").split(",").toSet.filter(_.nonEmpty)
  override val keySerializer: Serializer[String] = new StringSerializer
  override val valueSerializer: Serializer[String] = new StringSerializer
  override val consumerBootstrapServers: String = conf.getString("blockchainAnchoring.kafkaConsumer.bootstrapServers")
  override val consumerGroupId: String = conf.getString("blockchainAnchoring.kafkaConsumer.groupId")
  override val consumerMaxPollRecords: Int = conf.getInt("blockchainAnchoring.kafkaConsumer.maxPollRecords")
  override val consumerGracefulTimeout: Int = conf.getInt("blockchainAnchoring.kafkaConsumer.gracefulTimeout")
  override val producerBootstrapServers: String = conf.getString("blockchainAnchoring.kafkaProducer.bootstrapServers")
  override val metricsSubNamespace: String = conf.getString("blockchainAnchoring.kafkaConsumer.metricsSubNamespace")
  override val consumerReconnectBackoffMsConfig: Long = conf.getLong("blockchainAnchoring.kafkaConsumer.reconnectBackoffMsConfig")
  override val consumerReconnectBackoffMaxMsConfig: Long = conf.getLong("blockchainAnchoring.kafkaConsumer.reconnectBackoffMaxMsConfig")
  override val lingerMs: Int = conf.getInt("blockchainAnchoring.kafkaProducer.lingerMS")

  override def maxTimeAggregationSeconds: Long = 120
}
