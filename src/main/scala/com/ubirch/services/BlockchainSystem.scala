package com.ubirch.services

import com.ubirch.kafka.express.ConfigBase
import com.ubirch.kafka.util.Exceptions.NeedForPauseException
import com.ubirch.models.{ BalanceGaugeMetric, EthereumInternalMetrics, Response, TimeMetrics }
import com.ubirch.util.Exceptions._
import com.ubirch.util.Time

import com.typesafe.config.Config
import com.typesafe.scalalogging.{ LazyLogging, Logger }
import monix.eval.Task
import org.iota.client.Client
import org.iota.client.local.NativeAPI
import org.slf4j.LoggerFactory
import org.web3j.protocol.core.methods.request.Transaction

import java.math.BigInteger
import java.net.URL
import scala.annotation.tailrec
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
      if (pi.current >= max) {
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
    with ConfigBase {

    @transient
    override protected lazy val logger: Logger = Logger(
      LoggerFactory.getLogger(
        getClass.getName.split("\\$")
          .headOption.getOrElse(getClass.getName)
      )
    )

    import EthereumBaseProcessor._

    import org.web3j.crypto.{ Credentials, RawTransaction, TransactionEncoder, WalletUtils }
    import org.web3j.protocol.Web3j
    import org.web3j.protocol.core.methods.response.{ EthSendTransaction, TransactionReceipt }
    import org.web3j.protocol.core.{ DefaultBlockParameter, DefaultBlockParameterName }
    import org.web3j.protocol.http.HttpService
    import org.web3j.utils.Numeric

    final val credentialsPathAndFileName = config.getString("credentialsPathAndFileName")
    final val password = config.getString("password")
    final val address = config.getString("toAddress")
    final val networkInfo = config.getString("networkInfo")
    final val networkType = config.getString("networkType")
    final val maybeChainId = Try(config.getLong("chainId")).filter(_ > 0).toOption
    final val url = config.getString("url")
    final val DEFAULT_SLEEP_MILLIS = config.getLong("defaultSleepMillisForReceipt")
    final val MAX_RECEIPT_ATTEMPTS = config.getInt("maxReceiptAttempts")
    final val checkBalanceEveryInSeconds = config.getInt("checkBalanceEveryInSeconds")

    final val api = Web3j.build(new HttpService(url))
    final val credentials = WalletUtils.loadCredentials(password, new java.io.File(credentialsPathAndFileName))
    final val balanceCancelable = Balance.start(checkBalanceEveryInSeconds seconds)

    logger.info(
      "Basic values := " +
        "url={} " +
        "address={} " +
        "boot_gas_limit={} " +
        "chain_id={} " +
        "check_balance_every_in_seconds={} ",
      url,
      address,
      maybeChainId.getOrElse("-"),
      checkBalanceEveryInSeconds
    )

    def process(data: String): Either[Seq[Response], Throwable] = {

      var context = Context.empty

      try {

        val (isOK, verificationMessage) = verifyBalance

        if (isOK.isEmpty) {
          logger.info("Balance Monitor not yet started")
          Right(NeedForPauseException("Balance not yet checked", "Balance has not started"))
        } else if (!isOK.exists(x => x)) {
          logger.error(verificationMessage)
          Left(Nil)
        } else {

          val pendingNextCount = getCount(address, DefaultBlockParameterName.PENDING)
          val latestNextCount = getCount(address)

          logger.info("status=OK[get_nonce] next_count={} pendingNextCount={}", latestNextCount, pendingNextCount)

          val transaction = Transaction.createEthCallTransaction(address, address, Numeric.toHexString(data.getBytes()))

          val gasPrice = getGasPrice
          val gasLimitEstimated = estimateGas(transaction)

          gasPriceGauge.labels(namespace.value).set(gasPrice.doubleValue())
          gasLimitGauge.labels(namespace.value).set(gasLimitEstimated.doubleValue())

          val hexMessage = createRawTransactionAsHexMessage(
            address,
            Numeric.toHexString(data.getBytes()),
            BigInt((gasPrice.longValue() * 1.1).toLong).bigInteger,
            BigInt(gasLimitEstimated),
            latestNextCount,
            maybeChainId,
            credentials
          )

          logger.info("status=OK[in_process] gas_price={} gas_limit={} next_count={} pendingNextCount={} chain_id={} data={} hex={}", gasPrice, gasLimitEstimated, latestNextCount, pendingNextCount, maybeChainId.getOrElse("None"), data, hexMessage)

          val txHash = sendTransaction(hexMessage)
          val timedReceipt = Time.time(getReceipt(txHash))
          val response = timedReceipt.result.map { receipt =>

            context = context
              .addTxHash(txHash)
              .addTxHashDuration(timedReceipt.elapsed)
              .addGasPrice(gasPrice)
              .addGasLimit(gasLimitEstimated)
              .addGasUsed(receipt.getGasUsed)
              .addCumulativeGasUsed(receipt.getCumulativeGasUsed)

            logger.info("status=OK[sent] {}", context.toString)
            Response.Added(txHash, data, namespace.value, networkInfo, networkType)

          }.getOrElse {

            context = context
              .addTxHash(txHash)
              .addTxHashDuration(timedReceipt.elapsed)
              .addGasPrice(gasPrice)
              .addGasLimit(gasLimitEstimated)

            timeoutsCounter.labels(namespace.value).inc()

            logger.error("status=KO[timeout] {}", context.toString)
            Response.Timeout(txHash, data, namespace.value, networkInfo, networkType)

          }

          Left(List(response))

        }

      } catch {
        case _: GettingNonceException =>
          logger.info("status=KO[getting_nonce] {}", context.toString)
          Right(NeedForPauseException("Nonce", "Error getting next nonce"))
        case e: SendingTXException =>
          logger.error("status=KO[sending_tx] message={} error={} code={} data={} exceptionName={}", data, e.errorMessage, e.errorCode, e.errorData, e.getClass.getCanonicalName)
          if (e.errorCode == -32010 && e.errorMessage.contains("Insufficient funds")) {
            logger.error("Insufficient funds current_balance={}", Balance.currentBalance)
            Left(Nil)
          } else if (e.errorCode == -32000 && e.errorMessage.contains("intrinsic gas too low")) {
            logger.error("Seems that the Gas Limit is too low, try increasing it.")
            Left(Nil)
          } else if (e.errorCode == -32010 && e.errorMessage.contains("another transaction with same nonce")) {

            logger.info("status=KO[jump-simulation] {}", context.toString)

            Right(NeedForPauseException("Possible transaction running", e.errorMessage))

          } else if (e.errorCode == -32000 && e.errorMessage.contains("replacement transaction underpriced")) {
            Right(NeedForPauseException("Possible transaction running", e.errorMessage))
          } else if (e.errorCode == -32000 && e.errorMessage.contains("nonce too low")) {
            Right(NeedForPauseException("Nonce too low", e.errorMessage))
          } else Left(Nil)
        case _: NoTXHashException =>
          logger.info("status=KO[no_tx_hash] {}", context.toString)
          Left(Nil)
        case e: GettingTXReceiptException =>
          logger.error("status=KO[getting_tx_receipt] message={} error={} code={} data={} exceptionName={}", data, e.errorMessage, e.errorCode, e.errorData, e.getClass.getCanonicalName)
          Left(Nil)
        case e: Exception =>
          logger.error("Something critical happened: ", e)
          Right(e)

      } finally {

        txFeeGauge.labels(namespace.value).set(context.transactionFee.toDouble)
        gasUsedGauge.labels(namespace.value).set(context.gasUsed.toDouble)
        usedDeltaGauge.labels(namespace.value).set(context.usedDelta)
        txTimeGauge.labels(namespace.value).set(context.txHashDuration.toDouble)
      }

    }

    def verifyBalance: (Option[Boolean], String) = {
      val balance = Balance.currentBalance
      if (balance < 0) {
        (None, "Balance not yet checked. Retrying")
      } else if (balance == 0) {
        (Option(false), "Current balance is zero")
      } else {
        (Option(true), "All is good")
      }

    }

    def getReceipt(txHash: String, maxRetries: Int = MAX_RECEIPT_ATTEMPTS): Option[TransactionReceipt] = {

      def receipt: Option[TransactionReceipt] = {
        val getTransactionReceiptRequest = api.ethGetTransactionReceipt(txHash).send()
        if (getTransactionReceiptRequest.hasError) throw GettingTXReceiptException("Error getting transaction receipt ", Option(getTransactionReceiptRequest.getError))
        getTransactionReceiptRequest.getTransactionReceipt.asScala
      }

      @tailrec
      def go(count: Int, sleepInMillis: Long = DEFAULT_SLEEP_MILLIS): Option[TransactionReceipt] = {

        if (count == 0)
          None
        else {

          val maybeReceipt = receipt

          if (maybeReceipt.isEmpty) {
            logger.info("status=OK[waiting_receipt] receipt_attempt={} sleep_in_millis={}", count, sleepInMillis)
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

    def estimateGas(transaction: Transaction): BigInteger = {
      val gasLimitEstimatedResponse = api.ethEstimateGas(transaction).send()
      if (gasLimitEstimatedResponse.hasError) throw EstimatingTransactionException(s"Error estimating gas limit for address [$address]", Option(gasLimitEstimatedResponse.getError))
      gasLimitEstimatedResponse.getAmountUsed
    }

    def getGasPrice: BigInteger = {
      val gasPriceResponse = api.ethGasPrice().send()
      if (gasPriceResponse.hasError) throw GettingGasPriceException(s"Error getting gas price for address [$address]", Option(gasPriceResponse.getError))
      gasPriceResponse.getGasPrice
    }

    sys.addShutdownHook {
      logger.info("Shutting down blockchain_processor_system={} and balance monitor", namespace.value)
      balanceCancelable.cancel()
      api.shutdown()
    }

    def registerNewBalance(balance: BigInt): Unit = balanceGauge.labels(namespace.value).set(balance.toDouble)

    def queryBalance: (String, BigInt) = balance(address)

  }

  object EthereumBaseProcessor {
    case class Context(
        txHash: String,
        txHashDuration: Long,
        gasPrice: BigInt,
        gasLimit: BigInt,
        gasUsed: BigInt,
        cumulativeGasUsed: BigInt
    ) {

      def addTxHash(newTxHash: String): Context = this.copy(txHash = newTxHash)
      def addTxHashDuration(newTxHashDuration: Long): Context = this.copy(txHashDuration = newTxHashDuration)
      def addGasPrice(newGasPrice: BigInt): Context = this.copy(gasPrice = newGasPrice)
      def addGasLimit(newGasLimit: BigInt): Context = this.copy(gasLimit = newGasLimit)
      def addGasUsed(newGasUsed: BigInt): Context = this.copy(gasUsed = newGasUsed)
      def addCumulativeGasUsed(newCumulativeGasUsed: BigInt): Context = this.copy(cumulativeGasUsed = newCumulativeGasUsed)

      def transactionFee: BigInt = gasPrice * gasUsed
      def usedDelta: Double = gasUsed.toDouble / gasLimit.toDouble

      override def toString: String = {
        s"time_used=${txHashDuration}ns tx_fee=$transactionFee gas_price=$gasPrice gas_limit=$gasLimit gas=$gasUsed cumulative_gas_used=$cumulativeGasUsed used_against_limit=${usedDelta * 100}% transaction_hash=$txHash"
      }
    }

    object Context {
      def empty: Context = new Context("", 0, 0, 0, 0, 0)
    }
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

    final val config = Try(conf.getConfig("blockchainAnchoring." + namespace.value)).getOrElse(throw NoConfigObjectFoundException("No object found for this blockchain=" + namespace.value))
    final val urlAsString = config.getString("url")
    final val tag = config.getString("tag")
    final val networkInfo = config.getString("networkInfo")
    final val networkType = config.getString("networkType")
    final val url = new URL(urlAsString)

    checkLink()

    val api: Client = Client.Builder.withNode(url.toString).finish()

    logger.info("Basic values := url={} tag={}", url.toString, tag)

    override def process(data: Seq[String]): Either[Seq[Response], Throwable] = {

      if (data.isEmpty) {
        Left(Nil)
      } else {

        try {

          val responses = data.map { message =>

            logger.info("status=OK[in_process]={}", message)

            val index = (tag + message).take(20)
            val (duration, responses) = send(index, message)

            logger.info("status=OK[sent]:{} time_used={}mills", responses.id().toString, duration.toMillis)
            txTimeGauge.labels(namespace.value).set(duration.toNanos.toDouble)

            Response.Added(responses.id().toString, message, namespace.value, networkInfo, networkType)

          }

          Left(responses)

        } catch {
          case e: org.iota.client.local.ClientException =>
            logger.error("status=KO[ClientException] message={} error={} exceptionName={}", data.mkString(", "), e.getMessage, e.getClass.getCanonicalName)
            Right(pauseFold('ConnectorException, 3)(e, NeedForPauseException("Jota ConnectorException", e.getMessage)))
          case e: Exception =>
            logger.error("Something unexpected happened: ", e)
            Right(e)
        }
      }
    }

    def send(index: String, message: String) = {
      import monix.execution.Scheduler.Implicits.global

      import scala.concurrent.duration._

      val deadline = 55.seconds

      Task.delay(api.message.withIndexString(index).withDataString(message).finish)
        .timed
        .runSyncUnsafe(deadline)

    }

    def checkLink(): Unit = NativeAPI.verifyLink()

  }

}
