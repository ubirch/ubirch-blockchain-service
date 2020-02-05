package com.ubirch.services

import com.ubirch.kafka.express.{ ConfigBase, ExpressKafkaApp }
import com.ubirch.models.{ Response, TransactionMetrics }
import com.ubirch.util.JsonSupport

trait BucketPicker extends TransactionMetrics with ConfigBase {
  a: ExpressKafkaApp[String, String, Unit] =>

  import com.ubirch.models.BlockchainProcessors._
  import com.ubirch.models.BlockchainSystem._

  lazy val producerTopics: Set[String] = conf.getString("blockchainAnchoring.kafkaProducer.topics").split(",").toSet.filter(_.nonEmpty)
  lazy val blockchainType: BlockchainType = BlockchainType.fromString(conf.getString("blockchainAnchoring.type")).getOrElse(throw new Exception("No Blockchain type set"))
  lazy val blockchain: BlockchainProcessor[String] = blockchainType match {
    case EthereumType => EthereumProcessor
    case EthereumClassicType => EthereumClassicProcessor
    case IOTAType => IOTAProcessor
  }
  lazy val flush: Boolean = conf.getBoolean("flush")

  logger.info("Configured blockchain={}", blockchainType.value)

  override val process: Process = Process { consumerRecords =>

    if (!flush) {
      val data = consumerRecords.map(x => x.value())
      blockchain.process(data) match {
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

}
