package com.ubirch.util

import org.web3j.protocol.core.Response.Error

object Exceptions {

  abstract class BlockchainException(val message: String) extends Exception(message) {
    val name: String = this.getClass.getCanonicalName
  }

  abstract class EthereumBlockchainException(message: String, val error: Option[Error], val isCritical: Boolean) extends BlockchainException(message)

  case class GettingNonceException(override val message: String, override val error: Option[Error]) extends EthereumBlockchainException(message, error, false)
  case class GettingBalanceException(override val message: String, override val error: Option[Error]) extends EthereumBlockchainException(message, error, false)
  case class SendingTXException(override val message: String, override val error: Option[Error]) extends EthereumBlockchainException(message, error, false)
  case class NoTXHashException(override val message: String) extends EthereumBlockchainException(message, None, false)
  case class GettingTXReceiptExceptionTXException(override val message: String, override val error: Option[Error]) extends EthereumBlockchainException(message, error, false)

}
