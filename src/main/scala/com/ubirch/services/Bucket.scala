package com.ubirch.services

import com.ubirch.kafka.express.ExpressKafkaApp
import org.apache.kafka.common.serialization.{ Deserializer, Serializer, StringDeserializer, StringSerializer }

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

  override val maxTimeAggregationSeconds: Long = 120
}
