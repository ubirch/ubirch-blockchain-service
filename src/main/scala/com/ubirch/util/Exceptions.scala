package com.ubirch.util

import org.web3j.protocol.core.Response.Error

/**
  * Represents the principal exceptions for the system
  */
object Exceptions {

  abstract class BlockchainException(val message: String) extends Exception(message) {
    val name: String = this.getClass.getCanonicalName
  }

  case class NoConfigObjectFoundException(override val message: String) extends BlockchainException(message)
  case class NoBalanceException(override val message: String) extends BlockchainException(message)

  abstract class EthereumBlockchainException(message: String, val error: Option[Error], val isCritical: Boolean) extends BlockchainException(message) {
    val errorMessage: String = error.map(_.getMessage).getOrElse("No Message")
    val errorCode: Int = error.map(_.getCode).getOrElse(-99)
    val errorData: String = error.map(_.getData).getOrElse("No Data")
  }

  case class BuildingW3ConnectionException(override val message: String) extends EthereumBlockchainException(message, None, isCritical = true)
  case class NonceHasNotChangedException(override val message: String, override val error: Option[Error]) extends EthereumBlockchainException(message, error, isCritical = false)
  case class GettingNonceException(override val message: String, override val error: Option[Error]) extends EthereumBlockchainException(message, error, isCritical = false)
  case class GettingBalanceException(override val message: String, override val error: Option[Error]) extends EthereumBlockchainException(message, error, isCritical = false)
  case class SendingTXException(override val message: String, override val error: Option[Error]) extends EthereumBlockchainException(message, error, isCritical = false)
  case class NoTXHashException(override val message: String) extends EthereumBlockchainException(message, None, false)
  case class GettingTXReceiptException(override val message: String, override val error: Option[Error]) extends EthereumBlockchainException(message, error, isCritical = false)

}
