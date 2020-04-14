package com.ubirch.services

import com.ubirch.kafka.express.{ ConfigBase, ExpressKafkaApp }
import com.ubirch.models.{ Response, TransactionMetrics }
import com.ubirch.util.JsonSupport
import org.apache.kafka.common.serialization.{ Deserializer, Serializer, StringDeserializer, StringSerializer }

/**
  * Represents a simple object that keeps track of the config paths
  */
object ConfPaths {

  val NAMESPACE = "blockchainAnchoring.namespace"
  val PROCESSOR = "blockchainAnchoring.processor"

  val CONSUMER_TOPICS = "blockchainAnchoring.kafkaConsumer.topics"
  val CONSUMER_BOOTSTRAP_SERVERS = "blockchainAnchoring.kafkaConsumer.bootstrapServers"
  val CONSUMER_GROUP_ID = "blockchainAnchoring.kafkaConsumer.groupId"
  val CONSUMER_MAX_POLL_RECORDS = "blockchainAnchoring.kafkaConsumer.maxPollRecords"
  val CONSUMER_GRACEFUL_TIMEOUT = "blockchainAnchoring.kafkaConsumer.gracefulTimeout"
  val METRICS_SUB_NAMESPACE = "blockchainAnchoring.kafkaConsumer.metricsSubNamespace"
  val CONSUMER_RECONNECT_BACKOFF_MS_CONFIG = "blockchainAnchoring.kafkaConsumer.reconnectBackoffMsConfig"
  val CONSUMER_RECONNECT_BACKOFF_MAX_MS_CONFIG = "blockchainAnchoring.kafkaConsumer.reconnectBackoffMaxMsConfig"

  val PRODUCER_BOOTSTRAP_SERVERS = "blockchainAnchoring.kafkaProducer.bootstrapServers"
  val PRODUCER_TOPICS = "blockchainAnchoring.kafkaProducer.topics"
  val LINGER_MS = "blockchainAnchoring.kafkaProducer.lingerMS"

  val PROMETHEUS_PORT = "blockchainAnchoring.metrics.prometheus.port"

}

/**
  * Represent an abstraction for picking up messages to process.
  */
trait BucketPicker extends TransactionMetrics with ConfigBase {
  a: ExpressKafkaApp[String, String, Unit] =>

  import com.ubirch.services.BlockchainProcessors._
  import com.ubirch.services.BlockchainSystem._

  def getProcessor(namespace: Namespace): BlockchainProcessor[String] = {
    Option(conf.getString(ConfPaths.PROCESSOR)).filter(_.nonEmpty)
      .collect {
        case "com.ubirch.models.BlockchainProcessors.EthereumProcessor" => new EthereumProcessor(namespace)
        case "com.ubirch.models.BlockchainProcessors.IOTAProcessor" => new IOTAProcessor(namespace)
        case other => throw new Exception("Processor not supported := " + other)
      }
      .getOrElse(throw new Exception("No Blockchain processor set"))
  }

  lazy val producerTopics: Set[String] = conf.getString(ConfPaths.PRODUCER_TOPICS).split(",").toSet.filter(_.nonEmpty)

  lazy val namespace: Namespace = Option(conf.getString(ConfPaths.NAMESPACE))
    .filter(_.nonEmpty)
    .map(x => Namespace(x))
    .getOrElse(throw new Exception("No Blockchain namespace set"))

  lazy val blockchain: BlockchainProcessor[String] = getProcessor(namespace)

  lazy val flush: Boolean = conf.getBoolean("flush")

  logger.info("Configured namespace={}", namespace.value)
  logger.info("Configured blockchain_processor={}", Option(blockchain.getClass.getCanonicalName).getOrElse("Custom Processor"))

  override val process: Process = Process { consumerRecords =>

    if (!flush) {
      val data = consumerRecords.map(x => x.value())
      blockchain.process(data) match {
        case Left(responses) =>
          if (responses.isEmpty) {
            errorCounter.labels(namespace.value).inc()
            //No need to react to this response as this type of response is intended to be a not critical blockchain exception/error, with is
            //totally OK to just let go and continue with other values.
          } else {
            successCounter.labels(namespace.value).inc()
            responses.map { res =>
              producerTopics.map(topic => send(topic, JsonSupport.ToJson[Response](res).toString()))
            }
          }
        case Right(exception) =>
          errorCounter.labels(namespace.value).inc()
          throw exception
      }
    }

  }

}

/**
  * Represents the "generator" of data, the bucket. It is built upon Kafka Express
  */
trait Bucket extends ExpressKafkaApp[String, String, Unit] {

  override val keyDeserializer: Deserializer[String] = new StringDeserializer
  override val valueDeserializer: Deserializer[String] = new StringDeserializer
  override val consumerTopics: Set[String] = conf.getString(ConfPaths.CONSUMER_TOPICS).split(",").toSet.filter(_.nonEmpty)
  override val keySerializer: Serializer[String] = new StringSerializer
  override val valueSerializer: Serializer[String] = new StringSerializer
  override val consumerBootstrapServers: String = conf.getString(ConfPaths.CONSUMER_BOOTSTRAP_SERVERS)
  override val consumerGroupId: String = conf.getString(ConfPaths.CONSUMER_GROUP_ID)
  override val consumerMaxPollRecords: Int = conf.getInt(ConfPaths.CONSUMER_MAX_POLL_RECORDS)
  override val consumerGracefulTimeout: Int = conf.getInt(ConfPaths.CONSUMER_GRACEFUL_TIMEOUT)
  override val producerBootstrapServers: String = conf.getString(ConfPaths.PRODUCER_BOOTSTRAP_SERVERS)
  override val metricsSubNamespace: String = conf.getString(ConfPaths.METRICS_SUB_NAMESPACE)
  override val consumerReconnectBackoffMsConfig: Long = conf.getLong(ConfPaths.CONSUMER_RECONNECT_BACKOFF_MS_CONFIG)
  override val consumerReconnectBackoffMaxMsConfig: Long = conf.getLong(ConfPaths.CONSUMER_RECONNECT_BACKOFF_MAX_MS_CONFIG)
  override val lingerMs: Int = conf.getInt(ConfPaths.LINGER_MS)

  override val maxTimeAggregationSeconds: Long = 120
}
