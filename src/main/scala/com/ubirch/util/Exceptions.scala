package com.ubirch.util

import org.web3j.protocol.core.Response.Error

object Exceptions {

  abstract class BlockchainException(val message: String) extends Exception(message) {
    val name: String = this.getClass.getCanonicalName
  }

  abstract class EthereumBlockchainException(message: String, val error: Error, val isCritical: Boolean) extends BlockchainException(message)

  case class GettingNonceException(override val message: String, override val error: Error) extends EthereumBlockchainException(message, error, false)
  case class SendingTXException(override val message: String, override val error: Error) extends EthereumBlockchainException(message, error, false)
  case class GettingTXReceiptExceptionTXException(override val message: String, override val error: Error) extends EthereumBlockchainException(message, error, false)

}
