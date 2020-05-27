package com.ubirch.util

import com.ubirch.kafka.express.ConfigBase
import com.ubirch.services.BlockchainProcessors.EthereumProcessor
import com.ubirch.services.BlockchainSystem.Namespace
import org.web3j.protocol.core.DefaultBlockParameterName

object EthereumQuickAccess extends ConfigBase {

  def main(args: Array[String]): Unit = {

    val address = "0xC17E0AeB794Ccf893D77222fbeAe37a4dDf64d7F"

    val namespace = Namespace("ethereum")

    val ethereum = new EthereumProcessor(namespace)
    val latestNonce = ethereum.processor.getCount(address, DefaultBlockParameterName.LATEST)
    println("Latest: " + latestNonce)
    val pending = ethereum.processor.getCount(address, DefaultBlockParameterName.PENDING)
    println("Pending: " + pending)

    /**
      * Latest: 30105
      * Pending: 30106
      *
      * Latest: 30106
      * Pending: 30106
      *
      * Latest: 30107
      * Pending: 30108
      *
      * Latest: 30108
      * Pending: 30108
      */

  }

}
