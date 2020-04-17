package com.ubirch.services

import java.net.URL

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.express.ConfigBase
import com.ubirch.kafka.util.Exceptions.NeedForPauseException
import com.ubirch.models.{ BalanceGaugeMetric, EthereumInternalMetrics, Response, TimeMetrics }
import com.ubirch.util.Exceptions._
import com.ubirch.util.{ RunTimeHook, Time }

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.blocking
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

/**
  * Represents a Blockchain system compounded of a namespace and a processor
  */
object BlockchainSystem {

  case class Namespace(value: String)

  trait BlockchainProcessor[D] {
    def namespace: Namespace
    def process(data: Seq[D]): Either[Seq[Response], Throwable]
  }

  trait PauseControl {

    case class PauseControlItem(max: Int, current: Int)

    private var pauses: Map[Symbol, PauseControlItem] = Map.empty

    private def pauseItem(name: Symbol, max: Int): PauseControlItem = {
      val pi = pauses.get(name) match {
        case Some(value) => value.copy(current = value.current + 1)
        case None => PauseControlItem(max = max, current = 0)
      }
      pauses = pauses.updated(name, pi)
      pi
    }

    def pauseFold(name: Symbol, max: Int)(exception: Exception, needForPauseException: NeedForPauseException): Exception = {
      val pi = pauseItem(name, max)
      if(pi.current >= max) {
        pauses.updated(name, PauseControlItem(max = max, current = 0))
        exception
      } else {
        needForPauseException
      }

    }

  }



}

/**
  * Represents a container for the supported blockchain processors
  */
object BlockchainProcessors {
  import BlockchainSystem._

  /**
    * Represent an abstraction for Ethereum-based systems
    * @param config Represents the configuration object
    * @param namespace Represents the namespace for the current instance
    */
  abstract class EthereumBaseProcessor(config: Config, val namespace: Namespace)
    extends BalanceMonitor
    with BalanceGaugeMetric
    with EthereumInternalMetrics
    with TimeMetrics
    with RunTimeHook
    with ConfigBase
    with LazyLogging {

    import org.web3j.crypto.{ Credentials, RawTransaction, TransactionEncoder, WalletUtils }
    import org.web3j.protocol.Web3j
    import org.web3j.protocol.core.methods.response.{ EthSendTransaction, TransactionReceipt }
    import org.web3j.protocol.core.{ DefaultBlockParameter, DefaultBlockParameterName }
    import org.web3j.protocol.http.HttpService
    import org.web3j.utils.{ Convert, Numeric }

    final val credentialsPathAndFileName = config.getString("credentialsPathAndFileName")
    final val password = config.getString("password")
    final val address = config.getString("toAddress")
    final val bootGasPrice = config.getString("gasPrice")
    final val bootGasLimit: BigInt = config.getString("gasLimit").toInt
    final val networkInfo = config.getString("networkInfo")
    final val networkType = config.getString("networkType")
    final val maybeChainId = Try(config.getLong("chainId")).filter(_ > 0).toOption
    final val url = config.getString("url")
    final val DEFAULT_SLEEP_MILLIS = config.getInt("defaultSleepMillisForReceipt")
    final val MAX_RECEIPT_ATTEMPTS = config.getInt("maxReceiptAttempts")
    final val checkBalanceEveryInSeconds = config.getInt("checkBalanceEveryInSeconds")

    final val api = Web3j.build(new HttpService(url))
    final val credentials = WalletUtils.loadCredentials(password, new java.io.File(credentialsPathAndFileName))
    final val balanceCancelable = Balance.start(checkBalanceEveryInSeconds seconds)

    case class Context(
        txHash: String,
        txHashDuration: Long,
        gasPrice: BigInt,
        gasLimit: BigInt,
        gasUsed: BigInt,
        cumulativeGasUsed: BigInt,
        usedDelta: Double
    )

    logger.info("Basic boot values := url={} address={} boot_gas_price={} boot_gas_limit={} chain_id={}", url, address, bootGasPrice, bootGasLimit, maybeChainId.getOrElse("-"))

    def process(data: String): Either[Seq[Response], Throwable] = {

      val (gasPrice: BigInt, gasLimit: BigInt) = calcGasValues

      try {

        val (isOK, verificationMessage) = verifyBalance(gasPrice)

        if (isOK.isEmpty) {
          logger.info("Balance Monitor not yet started")
          Right(NeedForPauseException("Balance not yet checked", "Balance has not started"))
        } else if (!isOK.exists(x => x)) {
          logger.error(verificationMessage)
          Left(Nil)
        } else {

          gasPriceGauge.labels(namespace.value).set(gasPrice.toDouble)
          gasLimitGauge.labels(namespace.value).set(gasLimit.toDouble)

          val currentCount = getCount(address)

          val hexMessage = createRawTransactionAsHexMessage(address, data, gasPrice, gasLimit, currentCount, maybeChainId, credentials)

          logger.info("Sending transaction := data={} count={} chain_id={} hex={} ", data, currentCount, maybeChainId.getOrElse("None"), hexMessage)

          val txHash = sendTransaction(hexMessage)
          val timedReceipt = Time.time(getReceipt(txHash))
          val maybeResponse = timedReceipt.result.map { receipt =>

            val context = Context(
              txHash,
              timedReceipt.elapsed,
              gasPrice,
              gasLimit,
              receipt.getGasUsed,
              receipt.getCumulativeGasUsed,
              calcUsage(gasLimit, receipt.getGasUsed)
            )

            gasUsedGauge.labels(namespace.value).set(context.gasUsed.toDouble)
            usedDeltaGauge.labels(namespace.value).set(context.usedDelta)
            txTimeGauge.labels(namespace.value).set(context.txHashDuration)

            logger.info(
              "Got transaction_hash={} time_used={}ns gas_price={} gas_limit={} gas_used={} cumulative_gas_used={} used_against_limit={}%",
              context.txHash,
              context.txHashDuration,
              context.gasPrice,
              context.gasLimit,
              context.gasUsed,
              context.cumulativeGasUsed,
              context.usedDelta * 100
            )

            Response.Added(txHash, data, namespace.value, networkInfo, networkType)

          }.orElse {
            logger.error("Timeout for transaction_hash={}", txHash)
            Option(Response.Timeout(txHash, data, namespace.value, networkInfo, networkType))
          }

          Left(maybeResponse.toList)
        }

      } catch {
        case e: EthereumBlockchainException if !e.isCritical =>
          val errorMessage = e.error.map(_.getMessage).getOrElse("No Message")
          val errorCode = e.error.map(_.getCode).getOrElse(-99)
          val errorData = e.error.map(_.getData).getOrElse("No Data")
          logger.error("status=KO message={} error={} code={} data={} exceptionName={}", data, errorMessage, errorCode, errorData, e.getClass.getCanonicalName)
          if (errorCode == -32010 && errorMessage.contains("Insufficient funds")) {
            logger.error("Insufficient funds current_balance={}", Balance.currentBalance)
            Left(Nil)
          } else if (errorCode == -32000 && errorMessage.contains("intrinsic gas too low")) {
            logger.error("Seems that the Gas Limit is too low, try increasing it. gas_limit={}", gasLimit)
            Left(Nil)
          } else if (errorCode == -32010 && errorMessage.contains("another transaction with same nonce")) {
            Right(NeedForPauseException("Possible transaction running", errorMessage))
          } else if (errorCode == -32000 && errorMessage.contains("replacement transaction underpriced")) {
            Right(NeedForPauseException("Possible transaction running", errorMessage))
          } else if (errorCode == -32000 && errorMessage.contains("nonce too low")) {
            Right(NeedForPauseException("Nonce too low", errorMessage))
          } else Left(Nil)
        case e: Exception =>
          logger.error("Something critical happened: ", e)
          Right(e)
      }

    }

    def verifyBalance(gasPrice: BigInt): (Option[Boolean], String) = {
      val balance = Balance.currentBalance
      if (balance < 0) {
        (None, "Balance not yet checked. Retrying")
      } else if (balance == 0) {
        (Option(false), "Current balance is zero")
      } else if (balance < gasPrice.bigInteger) {
        (Option(false), "Current balance is less than the configured gas price")
      } else {
        (Option(true), "All is good")
      }

    }

    def calcGasValues: (BigInt, BigInt) = {
      val gasPrice: BigInt = Convert.toWei(bootGasPrice, Convert.Unit.GWEI).toBigInteger
      val gasLimit: BigInt = bootGasLimit
      (gasPrice, gasLimit)
    }

    def calcUsage(gasLimit: BigInt, gasUsed: BigInt): Double = gasUsed.toDouble / gasLimit.toDouble

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
            logger.info("receipt_attempt={} sleep_in_millis={} ...", count, sleepInMillis)
            val sleep = if (sleepInMillis <= 0) DEFAULT_SLEEP_MILLIS else sleepInMillis
            Thread.sleep(sleep)
            go(count - 1, sleep - 1000)
          } else maybeReceipt

        }

      }

      blocking(go(maxRetries))

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

    def createRawTransactionAsHexMessage(address: String, message: String, gasPrice: BigInt, gasLimit: BigInt, countOrNonce: BigInt, maybeChainId: Option[Long], credentials: Credentials): String = {
      val rawTransaction = RawTransaction.createTransaction(
        countOrNonce.bigInteger,
        gasPrice.bigInteger,
        gasLimit.bigInteger,
        address,
        message
      )

      val signedMessage = maybeChainId.map { chainId =>
        TransactionEncoder.signMessage(rawTransaction, chainId, credentials)
      }.getOrElse {
        TransactionEncoder.signMessage(rawTransaction, credentials)
      }

      Numeric.toHexString(signedMessage)

    }

    def getCount(address: String, blockParameter: DefaultBlockParameter = DefaultBlockParameterName.LATEST): BigInt = {
      val transactionCountResponse = api.ethGetTransactionCount(address, blockParameter).send()
      if (transactionCountResponse.hasError) throw GettingNonceException("Error getting transaction count(nonce)", Option(transactionCountResponse.getError))
      transactionCountResponse.getTransactionCount
    }

    def balance(address: String, blockParameter: DefaultBlockParameter = DefaultBlockParameterName.LATEST): (String, BigInt) = {
      val transactionCountResponse = api.ethGetBalance(address, blockParameter).send()
      if (transactionCountResponse.hasError) throw GettingBalanceException(s"Error getting balance for address [$address]", Option(transactionCountResponse.getError))
      (address, transactionCountResponse.getBalance)
    }

    def shutdownHook(): Unit = {
      logger.info("Shutting down blockchain_processor_system={} and balance monitor", namespace.value)
      balanceCancelable.cancel()
      api.shutdown()
    }

    def registerNewBalance(balance: BigInt): Unit = balanceGauge.labels(namespace.value).set(balance.toDouble)

    def queryBalance: (String, BigInt) = balance(address)

  }

  /**
    * Represents a concrete Ethereum Processor
    * This processor can be used with all Ethereum-based blockchains:
    * Ethereum itself and Classic supported out of the box
    * @param namespace Represents the namespace for the blockchain
    */

  class EthereumProcessor(val namespace: Namespace)
    extends BlockchainProcessor[String]
    with ConfigBase
    with LazyLogging {

    final val config: Config = Try(conf.getConfig("blockchainAnchoring." + namespace.value)).getOrElse(throw NoConfigObjectFoundException("No object found for this blockchain"))

    final val processor: EthereumBaseProcessor = new EthereumBaseProcessor(config, namespace) {}

    def process(data: Seq[String]): Either[Seq[Response], Throwable] =
      data.toList match {
        case List(d) => processor.process(d)
        case Nil => Left(Nil)
        case _ => Right(new Exception("Please configure for this blockchain a poll size of 1"))
      }
  }

  /**
    * Represents a concrete IOTA processor
    * @param namespace Represents the namespace for the blockchain
    */
  class IOTAProcessor(val namespace: Namespace)
    extends BlockchainProcessor[String]
    with PauseControl
    with TimeMetrics
    with ConfigBase
    with LazyLogging {

    import org.iota.jota.IotaAPI
    import org.iota.jota.model.Transfer
    import org.iota.jota.utils.TrytesConverter

    final val config = Try(conf.getConfig("blockchainAnchoring." + namespace.value)).getOrElse(throw NoConfigObjectFoundException("No object found for this blockchain=" + namespace.value))
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

    override def process(data: Seq[String]): Either[Seq[Response], Throwable] = {

      if (data.isEmpty) {
        Left(Nil)
      } else {

        logger.info("transfer_data={}", data.mkString(", "))

        try {

          val transfers = data.map { x =>
            val trytes = TrytesConverter.asciiToTrytes(x) // Note: if message > 2187 Trytes, it is sent in several transactions
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

          val timedTransactionsAndMessages = Time.time(response.getTransactions.asScala.toList.zip(data))
          val responses = timedTransactionsAndMessages.result.map { case (tx, data) =>
            logger.info("Got transaction_hash={} time_used={}ns", tx.getHash, timedTransactionsAndMessages.elapsed)
            txTimeGauge.labels(namespace.value).set(timedTransactionsAndMessages.elapsed)

            Response.Added(tx.getHash, data, namespace.value, networkInfo, networkType)
          }

          Left(responses)
        } catch {
          case e: org.iota.jota.error.ConnectorException =>
            logger.error("status=KO message={} error={} code={} exceptionName={}", data.mkString(", "), e.getMessage, e.getErrorCode, e.getClass.getCanonicalName)
            Right(pauseFold('ConnectorException, 30)(e, NeedForPauseException("Jota ConnectorException", e.getMessage)))
          case e: org.iota.jota.error.InternalException =>
            logger.error("status=KO message={} error={} exceptionName={}", data.mkString(", "), e.getMessage, e.getClass.getCanonicalName)
            Right(pauseFold('InternalException, 30)(e, NeedForPauseException("Jota InternalException", e.getMessage)))
          case e: Exception =>
            logger.error("Something critical happened: ", e)
            Right(e)

        }
      }
    }

    def balance(threshold: Int = 100 /*based on confirmed transactions*/ ): Long = api.getBalance(threshold, completeAddress)

  }

}