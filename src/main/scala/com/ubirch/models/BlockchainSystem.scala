package com.ubirch.models

import java.net.URL

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.express.ConfigBase
import com.ubirch.services.BalanceMonitor
import com.ubirch.util.Exceptions._
import org.iota.jota.model.Transfer
import org.iota.jota.utils.TrytesConverter

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.language.higherKinds
import scala.util.Try

object BlockchainSystem {

  case class Data(value: String)

  trait BlockchainProcessor[Block[_], D] {
    def process(data: Seq[D]): Either[Seq[Response], Throwable]
  }

  case class EthereumBlockchain[D](data: Seq[D])(implicit val processor: BlockchainProcessor[EthereumBlockchain, D]) {
    def process = processor.process(data)
  }

  case class EthereumClassicBlockchain[D](data: Seq[D])(implicit processor: BlockchainProcessor[EthereumBlockchain, D]) {
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

  implicit object EthereumProcessor
    extends BlockchainProcessor[EthereumBlockchain, Data]
    with BalanceMonitor
    with WithExecutionContext
    with ConfigBase
    with LazyLogging {

    import org.web3j.crypto.{ RawTransaction, TransactionEncoder, WalletUtils }
    import org.web3j.protocol.Web3j
    import org.web3j.protocol.core.DefaultBlockParameterName
    import org.web3j.protocol.core.methods.response.{ EthSendTransaction, TransactionReceipt }
    import org.web3j.protocol.http.HttpService
    import org.web3j.utils.{ Convert, Numeric }

    final val config = Try(conf.getConfig("blockchainAnchoring.ethereum")).getOrElse(throw NoConfigObjectFoundException("No object found for this blockchain"))
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

    final val api = Web3j.build(new HttpService(url))
    final val credentials = WalletUtils.loadCredentials(password, new java.io.File(credentialsPathAndFileName))
    Balance.start()

    override def queryBalance: BigInt = balance()

    def verifyBalance = {

      val balance = Balance.currentBalance
      if (balance <= 0) {
        (false, "Current balance is zero")
      } else if (balance < Convert.toWei(gasPrice, Convert.Unit.GWEI).toBigInteger) {
        (false, "Current balance is less than the configured gas price")
      } else {
        (true, "All is good")
      }

    }

    override def process(data: Seq[Data]): Either[Seq[Response], Throwable] = {

      if (data.isEmpty) {
        Left(Nil)
      } else {

        val message = data.headOption.map(_.value).getOrElse("")

        try {

          val (isOK, verificationMessage) = verifyBalance

          if (!isOK) {
            logger.error(verificationMessage)
            Left(Nil)
          } else {

            val count = getCount()
            val hexMessage = createTransactionAsHexMessage(message, count)

            logger.info("Sending transaction={} with count={}", message, count)
            val txHash = sendTransaction(hexMessage)
            val maybeResponse = getReceipt(txHash).map { _ =>
              Response.Added(txHash, message, EthereumType.value, networkInfo, networkType)
            }.orElse {
              Option(Response.Timeout(txHash, message, EthereumType.value, networkInfo, networkType))
            }

            Left(maybeResponse.toList)
          }

        } catch {
          case e: EthereumBlockchainException if !e.isCritical =>
            val errorMessage = e.error.map(_.getMessage).getOrElse("No Message")
            val errorCode = e.error.map(_.getCode).getOrElse("No Error Code")
            val errorData = e.error.map(_.getData).getOrElse("No Data")
            logger.error("status=KO message={} error={} code={} data={} exceptionName={}", message, errorMessage, errorCode, errorData, e.getClass.getCanonicalName)
            Left(Nil)
          case e: Exception =>
            logger.error("Something critical happened: ", e)
            Right(e)
        }
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

          logger.info("receipt_attempt={} sleepInMillis={} ...", count, sleepInMillis)

          val maybeReceipt = receipt

          if (maybeReceipt.isEmpty) {
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
      if (transactionCountResponse.hasError) throw GettingBalanceException(s"Error getting balance for address [${address}]", Option(transactionCountResponse.getError))
      transactionCountResponse.getBalance
    }

    def getCount(blockParameterName: DefaultBlockParameterName = DefaultBlockParameterName.LATEST): BigInt = {
      val transactionCountResponse = api.ethGetTransactionCount(address, blockParameterName).send()
      if (transactionCountResponse.hasError) throw GettingNonceException("Error getting transaction count(nonce)", Option(transactionCountResponse.getError))
      transactionCountResponse.getTransactionCount
    }

  }

  implicit object IOTAProcessor extends BlockchainProcessor[IOTABlockchain, Data] with ConfigBase {

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
    final val createIOTATransferTree = config.getBoolean("createIOTATransferTree")
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

        val messages = if (createIOTATransferTree) data else data.headOption.toList

        val transfers = messages.map { x =>
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

        val transactionsAndMessages = response.getTransactions.asScala.toList.zip(messages)

        val responses = transactionsAndMessages.map { case (tx, data) =>
          Response.Added(tx.getHash, data.value, EthereumType.value, networkInfo, networkType)
        }

        Left(responses)
      }
    }

    def balance(threshold: Int = 100 /*based on confirmed transactions*/ ) = api.getBalance(threshold, completeAddress)

  }

}
