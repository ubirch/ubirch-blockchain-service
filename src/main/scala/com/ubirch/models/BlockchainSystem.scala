package com.ubirch.models

import java.io.{ BufferedReader, IOException, InputStream, InputStreamReader }
import java.util.Date

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.express.ConfigBase
import com.ubirch.models.BlockchainProcessors.EthereumProcessor.getClass
import com.ubirch.services.BlockchainBucket.conf
import com.ubirch.util.Exceptions.{ EthereumBlockchainException, GettingNonceException, GettingTXReceiptExceptionTXException, NoTXHashException, SendingTXException }
import org.bouncycastle.util.encoders.Hex
import org.web3j.crypto.{ RawTransaction, SignedRawTransaction, TransactionEncoder, WalletUtils }
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.{ DefaultBlockParameter, DefaultBlockParameterName }
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import org.web3j.utils.Convert
import org.web3j.utils.Numeric

import scala.annotation.tailrec
import scala.compat.java8.OptionConverters._
import scala.language.higherKinds

object BlockchainSystem {

  case class Data(value: String)

  trait BlockchainProcessor[Block[_], D] {
    def process(data: Seq[D]): Either[Option[Response], Throwable]
  }

  case class EthereumBlockchain[D](data: Seq[D])(implicit processor: BlockchainProcessor[EthereumBlockchain, D]) {
    def process = processor.process(data)
  }

  case class EthereumClassicBlockchain[D](data: Seq[D])(implicit processor: BlockchainProcessor[EthereumClassicBlockchain, D]) {
    def process = processor.process(data)
  }

  case class IOTABlockchain[D](data: Seq[D])(implicit processor: BlockchainProcessor[IOTABlockchain, D]) {
    def process = processor.process(data)
  }
  //

  sealed trait BlockchainType {
    val value: String
  }

  object BlockchainType {
    def isValid(value: String): Boolean = fromString(value).isDefined
    def fromString(value: String): Option[BlockchainType] = options.find(_.value == value)
    def options: List[BlockchainType] = List(EthereumType, EthereumClassicType, IOTAType)
  }

  case object EthereumType extends BlockchainType {
    override val value: String = "ethereum"
  }
  case object EthereumClassicType extends BlockchainType {
    override val value: String = "ethereum-classic"
  }
  case object IOTAType extends BlockchainType {
    override val value: String = "iota"
  }

}

object BlockchainProcessors {
  import BlockchainSystem._

  implicit object EthereumProcessor extends BlockchainProcessor[EthereumBlockchain, Data] with ConfigBase with LazyLogging {

    val config = conf.getConfig("blockchainAnchoring.ethereum")

    val credentialsPathAndFileName = config.getString("credentialsPathAndFileName")
    val password = config.getString("password")

    val address = config.getString("toAddress")
    val gasPrice = config.getString("gasPrice")
    val gasLimit: BigInt = config.getString("gasLimit").toInt

    val networkInfo = config.getString("networkInfo")
    val networkType = config.getString("networkType")
    val chainId = config.getInt("chainId")

    val url = config.getString("url")
    val web3 = Web3j.build(new HttpService(url))
    val credentials = WalletUtils.loadCredentials(password, new java.io.File(credentialsPathAndFileName))

    override def process(data: Seq[Data]): Either[Option[Response], Throwable] = {

      val message = data.headOption.map(_.value).getOrElse("")

      try {

        val transactionCountResponse = web3.ethGetTransactionCount(address, DefaultBlockParameterName.LATEST).send()
        if (transactionCountResponse.hasError) throw GettingNonceException("Error getting transaction count(nonce)", Option(transactionCountResponse.getError))

        val rawTransaction = RawTransaction.createTransaction(
          transactionCountResponse.getTransactionCount,
          Convert.toWei(gasPrice, Convert.Unit.GWEI).toBigInteger,
          gasLimit.bigInteger,
          address,
          message
        )

        val signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, credentials)
        val hexMessage = Numeric.toHexString(signedMessage)
        val sendTransactionResponse = web3.ethSendRawTransaction(hexMessage).send()
        if (sendTransactionResponse.hasError) throw SendingTXException("Error sending transaction ", Option(sendTransactionResponse.getError))

        val txHash = sendTransactionResponse.getTransactionHash

        if (txHash == null || txHash.isEmpty) {
          throw NoTXHashException("No transaction hash retrieved after sending ")
        }

        def getReceipt(maxRetries: Int = 10) = {

          @tailrec
          def go(count: Int): Option[Response] = {

            if (count == 0) {
              logger.error("tx=KO")
              Option(Response.Timeout(sendTransactionResponse.getTransactionHash, message, EthereumType.value, networkInfo, networkType))
            } else {

              logger.info("Trying to get tx receipt, retry={} ...", count)

              val getTransactionReceiptRequest = web3.ethGetTransactionReceipt(txHash).send()
              if (sendTransactionResponse.hasError) throw GettingTXReceiptExceptionTXException("Error sending transaction ", Option(sendTransactionResponse.getError))

              val maybeTransactionReceipt = getTransactionReceiptRequest.getTransactionReceipt.asScala

              val maybeReceipt = maybeTransactionReceipt.map { _ =>
                logger.info("tx=OK")
                Response.Added(sendTransactionResponse.getTransactionHash, message, EthereumType.value, networkInfo, networkType)
              }

              if (maybeReceipt.isEmpty) {
                Thread.sleep(5000)
                go(count - 1)
              } else maybeReceipt

            }

          }

          go(maxRetries)

        }

        Left(getReceipt())

      } catch {
        case e: EthereumBlockchainException if !e.isCritical =>
          val errorMessage = e.error.map(_.getMessage).getOrElse("No Message")
          val errorCode = e.error.map(_.getCode).getOrElse("No Error Code")
          val errorData = e.error.map(_.getData).getOrElse("No Data")
          logger.error("tx=KO message={} error={} code={} data={} exceptionName={}", message, errorMessage, errorCode, errorData, e.getClass.getCanonicalName)
          Left(None)
        case e: Exception =>
          logger.error("Something critical happened: ", e)
          Right(e)
      }

    }
  }

  implicit object EthereumClassicProcessor extends BlockchainProcessor[EthereumClassicBlockchain, Data] {
    override def process(data: Seq[Data]): Either[Option[Response], Throwable] = Left(None)
  }

  implicit object IOTAProcessor extends BlockchainProcessor[IOTABlockchain, Data] {
    override def process(data: Seq[Data]): Either[Option[Response], Throwable] = Left(None)
  }

}
