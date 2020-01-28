package com.ubirch.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.BlockchainProcessors.IOTAProcessor
import org.iota.jota.builder.AddressRequest

object IOTAAddressGenerator extends LazyLogging {


  def createAddress(seed: String, securityLevel: Int = 2)  = {
    try {
      val response = IOTAProcessor.api.generateNewAddresses(
        new AddressRequest.Builder(seed, securityLevel)
          .amount(1)
          .checksum(true)
          .build
      )
      response
    } catch {
      case e: Exception =>
        logger.error("Error creating address={} exception_name={}", e.getMessage, e.getClass.getCanonicalName)
        throw e

    }
  }

  def main(args: Array[String]): Unit = {

    /**
     * You can run cat /dev/urandom |tr -dc A-Z9|head -c${1:-81} to generate a seed
     */

    val seed = "This is a seed."
    val securityLevel = 2

    val response = createAddress(seed, securityLevel)

    logger.info("Your address is {}", response.getAddresses)

  }


}
