package com.ubirch.models

import java.net.URL

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.express.ConfigBase
import com.ubirch.kafka.util.Exceptions.NeedForPauseException
import com.ubirch.services.BalanceMonitor
import com.ubirch.util.Exceptions._
import com.ubirch.util.RunTimeHook
import org.iota.jota.model.Transfer
import org.iota.jota.utils.TrytesConverter

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration._
import scala.language.{ higherKinds, postfixOps }
import scala.util.Try

object BlockchainSystem {

  case class Data(value: String)

  trait BlockchainProcessor[Block[_], D] {
    def process(data: Seq[D]): Either[Seq[Response], Throwable]
  }

  case class EthereumBlockchain[D](data: Seq[D])(implicit val processor: BlockchainProcessor[EthereumBlockchain, D]) {
    def process = processor.process(data)
  }

  case class EthereumClassicBlockchain[D](data: Seq[D])(implicit processor: BlockchainProcessor[EthereumClassicBlockchain, D]) {
    def process = processor.process(data)
  }

  case class IOTABlockchain[D](data: Seq[D])(implicit processor: BlockchainProcessor[IOTABlockchain, D]) {
    def process = processor.process(data)
  }

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

  abstract class EthereumBaseProcessor(config: Config, blockchainType: BlockchainType)
    extends BalanceMonitor
    with RunTimeHook
    with WithExecutionContext
    with ConfigBase
    with LazyLogging {

    import org.web3j.crypto.{ RawTransaction, TransactionEncoder, WalletUtils }
    import org.web3j.protocol.Web3j
    import org.web3j.protocol.core.DefaultBlockParameterName
    import org.web3j.protocol.core.methods.response.{ EthSendTransaction, TransactionReceipt }
    import org.web3j.protocol.http.HttpService
    import org.web3j.utils.{ Convert, Numeric }

    final val credentialsPathAndFileName = config.getString("credentialsPathAndFileName")
    final val password = config.getString("password")
    final val address = config.getString("toAddress")
    final val gasPrice = config.getString("gasPrice")
    final val gasLimit: BigInt = config.getString("gasLimit").toInt
    final val networkInfo = config.getString("networkInfo")
    final val networkType = config.getString("networkType")
    final val chainId = config.getInt("chainId")
    final val url = config.getString("url")
    final val DEFAULT_SLEEP_MILLIS = config.getInt("defaultSleepMillisForReceipt")
    final val MAX_RECEIPT_ATTEMPTS = config.getInt("maxReceiptAttempts")
    final val checkBalanceEveryInSeconds = config.getInt("checkBalanceEveryInSeconds")

    final val api = Web3j.build(new HttpService(url))
    final val credentials = WalletUtils.loadCredentials(password, new java.io.File(credentialsPathAndFileName))
    final val balanceCancelable = Balance.start(checkBalanceEveryInSeconds seconds)

    def verifyBalance: (Boolean, BigInt, String) = {

      val balance = Balance.currentBalance
      if (balance <= 0) {
        (false, balance, "Current balance is zero")
      } else if (balance < Convert.toWei(gasPrice, Convert.Unit.GWEI).toBigInteger) {
        (false, balance, "Current balance is less than the configured gas price")
      } else {
        (true, balance, "All is good")
      }

    }

    def process(data: Data): Either[Seq[Response], Throwable] = {

      val message = data.value

      try {

        val (isOK, _, verificationMessage) = verifyBalance

        if (!isOK) {
          logger.error(verificationMessage)
          Left(Nil)
        } else {

          val currentCount = getCount()
          val hexMessage = createTransactionAsHexMessage(message, currentCount)

          logger.info("Sending transaction={} with count={}", message, currentCount)
          val txHash = sendTransaction(hexMessage)
          val maybeResponse = getReceipt(txHash).map { _ =>
            logger.info("Got transaction_hash={}", txHash)
            Response.Added(txHash, message, blockchainType.value, networkInfo, networkType)
          }.orElse {
            logger.error("Timeout for transaction_hash={}", txHash)
            Option(Response.Timeout(txHash, message, blockchainType.value, networkInfo, networkType))
          }

          Left(maybeResponse.toList)
        }

      } catch {
        case e: EthereumBlockchainException if !e.isCritical =>
          val errorMessage = e.error.map(_.getMessage).getOrElse("No Message")
          val errorCode = e.error.map(_.getCode).getOrElse(-99)
          val errorData = e.error.map(_.getData).getOrElse("No Data")
          logger.error("status=KO message={} error={} code={} data={} exceptionName={}", message, errorMessage, errorCode, errorData, e.getClass.getCanonicalName)
          if (errorCode == -32010 && errorMessage.contains("Insufficient funds"))
            Left(Nil) //Right(NeedForPauseException("Insufficient funds.", errorMessage))
          else if (errorCode == -32010 && errorMessage.contains("another transaction with same nonce"))
            Right(NeedForPauseException("Possible transaction running", errorMessage))
          else if (errorCode == -32000 && errorMessage.contains("replacement transaction underpriced"))
            Right(NeedForPauseException("Possible transaction running", errorMessage))
          else if (errorCode == -32000 && errorMessage.contains("nonce too low"))
            Right(NeedForPauseException("Nonce too low", errorMessage))
          else Left(Nil)
        case e: Exception =>
          logger.error("Something critical happened: ", e)
          Right(e)
      }

    }

    def getReceipt(txHash: String, maxRetries: Int = MAX_RECEIPT_ATTEMPTS): Option[TransactionReceipt] = {

      def receipt: Option[TransactionReceipt] = {
        val getTransactionReceiptRequest = api.ethGetTransactionReceipt(txHash).send()
        if (getTransactionReceiptRequest.hasError) throw GettingTXReceiptExceptionTXException("Error getting transaction receipt ", Option(getTransactionReceiptRequest.getError))
        getTransactionReceiptRequest.getTransactionReceipt.asScala
      }

      @tailrec
      def go(count: Int, sleepInMillis: Int = DEFAULT_SLEEP_MILLIS): Option[TransactionReceipt] = {

        if (count == 0)
          None
        else {

          val maybeReceipt = receipt

          if (maybeReceipt.isEmpty) {
            logger.info("receipt_attempt={} sleepInMillis={} ...", count, sleepInMillis)
            val sleep = if (sleepInMillis <= 0) DEFAULT_SLEEP_MILLIS else sleepInMillis
            Thread.sleep(sleep)
            go(count - 1, sleep - 1000)
          } else maybeReceipt

        }

      }

      go(maxRetries)

    }

    def sendTransaction(hexMessage: String): String = {
      val sendTransactionResponse: EthSendTransaction = api.ethSendRawTransaction(hexMessage).send()
      if (sendTransactionResponse.hasError) throw SendingTXException("Error sending transaction ", Option(sendTransactionResponse.getError))
      val txHash = sendTransactionResponse.getTransactionHash
      if (txHash == null || txHash.isEmpty) {
        throw NoTXHashException("No transaction hash retrieved after sending ")
      }

      txHash
    }

    def createTransactionAsHexMessage(message: String, countOrNonce: BigInt): String = {
      val rawTransaction = RawTransaction.createTransaction(
        countOrNonce.bigInteger,
        Convert.toWei(gasPrice, Convert.Unit.GWEI).toBigInteger,
        gasLimit.bigInteger,
        address,
        message
      )

      val signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, credentials)
      val hexMessage = Numeric.toHexString(signedMessage)

      hexMessage
    }

    def balance(blockParameterName: DefaultBlockParameterName = DefaultBlockParameterName.LATEST): BigInt = {
      val transactionCountResponse = api.ethGetBalance(address, blockParameterName).send()
      if (transactionCountResponse.hasError) throw GettingBalanceException(s"Error getting balance for address [$address]", Option(transactionCountResponse.getError))
      transactionCountResponse.getBalance
    }

    def getCount(blockParameterName: DefaultBlockParameterName = DefaultBlockParameterName.LATEST): BigInt = {
      val transactionCountResponse = api.ethGetTransactionCount(address, blockParameterName).send()
      if (transactionCountResponse.hasError) throw GettingNonceException("Error getting transaction count(nonce)", Option(transactionCountResponse.getError))
      transactionCountResponse.getTransactionCount
    }

    override def shutdownHook(): Unit = {
      logger.info("Shutting down blockchain_processor_system={} and balance monitor", blockchainType.value)
      balanceCancelable.cancel()
      api.shutdown()
    }

  }

  implicit object EthereumProcessor
    extends BlockchainProcessor[EthereumBlockchain, Data]
    with BalanceGaugeMetric
    with ConfigBase
    with LazyLogging {

    final val config = Try(conf.getConfig("blockchainAnchoring." + EthereumType.value)).getOrElse(throw NoConfigObjectFoundException("No object found for this blockchain"))

    val processor = new EthereumBaseProcessor(config, EthereumType) {

      override def registerNewBalance(balance: BigInt): Unit = balanceGauge.labels(EthereumType.value).set(balance.toDouble)

      override def queryBalance: BigInt = balance()
    }

    override def process(data: Seq[Data]): Either[Seq[Response], Throwable] =
      data.toList match {
        case List(d) => processor.process(d)
        case Nil => Left(Nil)
        case _ => Right(new Exception("Please configure for this blockchain a poll size of 1"))
      }

  }

  implicit object EthereumClassicProcessor
    extends BlockchainProcessor[EthereumClassicBlockchain, Data]
    with BalanceGaugeMetric
    with ConfigBase
    with LazyLogging {

    final val config = Try(conf.getConfig("blockchainAnchoring." + EthereumClassicType.value)).getOrElse(throw NoConfigObjectFoundException("No object found for this blockchain"))

    val processor = new EthereumBaseProcessor(config, EthereumClassicType) {

      override def registerNewBalance(balance: BigInt): Unit = balanceGauge.labels(EthereumClassicType.value).set(balance.toDouble)

      override def queryBalance: BigInt = balance()
    }

    override def process(data: Seq[Data]): Either[Seq[Response], Throwable] =
      data.toList match {
        case List(d) => processor.process(d)
        case Nil => Left(Nil)
        case _ => Right(new Exception("Please configure for this blockchain a poll size of 1"))
      }

  }

  implicit object IOTAProcessor extends BlockchainProcessor[IOTABlockchain, Data] with ConfigBase with LazyLogging {

    import org.iota.jota.IotaAPI

    final val config = Try(conf.getConfig("blockchainAnchoring.iota")).getOrElse(throw NoConfigObjectFoundException("No object found for this blockchain"))
    final val urlAsString = config.getString("url")
    final val address = config.getString("toAddress")
    final val addressChecksum = config.getString("toAddressChecksum")
    final val completeAddress = address + addressChecksum
    final val depth = config.getInt("depth")
    final val seed = config.getString("seed")
    final val securityLevel = config.getInt("securityLevel")
    final val minimumWeightMagnitude = config.getInt("minimumWeightMagnitude")
    final val tag = config.getString("tag")
    final val networkInfo = config.getString("networkInfo")
    final val networkType = config.getString("networkType")

    final val url = new URL(urlAsString)
    final val api = new IotaAPI.Builder()
      .protocol(url.getProtocol)
      .host(url.getHost)
      .port(url.getPort)
      .build()

    override def process(data: Seq[Data]): Either[Seq[Response], Throwable] = {

      if (data.isEmpty) {
        Left(Nil)
      } else {

        logger.info("transfer_data={}", data.mkString(", "))

        try {

          val transfers = data.map { x =>
            val trytes = TrytesConverter.asciiToTrytes(x.value) // Note: if message > 2187 Trytes, it is sent in several transactions
            new Transfer(completeAddress, 0, trytes, tag)
          }

          val response = api.sendTransfer(
            seed,
            securityLevel,
            depth,
            minimumWeightMagnitude,
            transfers.asJava,
            null,
            null,
            false,
            false,
            null
          )

          val transactionsAndMessages = response.getTransactions.asScala.toList.zip(data)

          val responses = transactionsAndMessages.map { case (tx, data) =>
            logger.info("Got transaction_hash={}", tx.getHash)
            Response.Added(tx.getHash, data.value, IOTAType.value, networkInfo, networkType)
          }

          Left(responses)
        } catch {
          case e: org.iota.jota.error.ConnectorException =>
            logger.error("status=KO message={} error={} code={} exceptionName={}", data.map(_.value).mkString(", "), e.getMessage, e.getErrorCode, e.getClass.getCanonicalName)
            Right(NeedForPauseException("Jota ConnectorException", e.getMessage))
          case e: org.iota.jota.error.InternalException =>
            logger.error("status=KO message={} error={} exceptionName={}", data.map(_.value).mkString(", "), e.getMessage, e.getClass.getCanonicalName)
            Right(NeedForPauseException("Jota InternalException", e.getMessage))
          case e: Exception =>
            logger.error("Something critical happened: ", e)
            Right(e)

        }
      }
    }

    def balance(threshold: Int = 100 /*based on confirmed transactions*/ ) = api.getBalance(threshold, completeAddress)

  }

}
