package com.ubirch.services

import java.util.Date

import com.typesafe.config.{ Config, ConfigFactory }
import com.ubirch.TestBase
import com.ubirch.models.{ Response, WithExecutionContext }
import com.ubirch.services.BlockchainSystem.{ BlockchainProcessor, Namespace }
import com.ubirch.util.JsonSupport.formats
import com.ubirch.util.PortGiver
import net.manub.embeddedkafka.EmbeddedKafkaConfig

class BucketPickerSpec extends TestBase {

  "A Bucket Picker System" must {

    "consume and send response to topic in form a status 'added'" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val message = "what I want sent"
      val inTopic = "com.ubirch.kafkatemplate.inbox"
      val outTopic = "com.ubirch.kafkatemplate.outbox"
      val date = new Date()

      val consumer = new Bucket with BucketPicker with WithExecutionContext {

        override def conf: Config = ConfigFactory.load("application-test.conf")

        override def getProcessor(_namespace: Namespace): BlockchainProcessor[String] = {
          new BlockchainProcessor[String] {

            override lazy val namespace: BlockchainSystem.Namespace = _namespace

            override def process(data: Seq[String]): Either[Seq[Response], Throwable] = {
              val resp = data.map { d =>
                Response
                  .Added("tx-id", d, namespace.value, "network-info", "network-type")
                  .withCreated(date)
              }
              Left(resp)
            }
          }
        }

        override val consumerBootstrapServers: String = bootstrapServers
        override val producerBootstrapServers: String = bootstrapServers
        override val consumerTopics = Set(inTopic)
        override lazy val producerTopics = Set(outTopic)
      }

      withRunningKafka {
        publishStringMessageToKafka(inTopic, message)
        consumer.consumption.startPolling()
        Thread.sleep(5000)
        val txRes = consumeFirstStringMessageFrom(outTopic)
        val expected = s"""{"status":"added","txid":"tx-id","message":"$message","blockchain":"iota","network_info":"network-info","network_type":"network-type","created":"${formats.dateFormat.format(date)}"}""".stripMargin
        assert(expected == txRes)
      }

    }
  }

}
