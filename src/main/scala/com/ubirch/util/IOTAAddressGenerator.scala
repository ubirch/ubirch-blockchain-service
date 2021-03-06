package com.ubirch.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.BlockchainProcessors.IOTAProcessor
import com.ubirch.services.BlockchainSystem.Namespace
import org.iota.jota.builder.AddressRequest
import org.iota.jota.dto.response.GetNewAddressResponse

/**
  * Represents a tool for generating IOTA addresses
  */
object IOTAAddressGenerator extends LazyLogging {

  def createAddress(seed: String, securityLevel: Int = 2)(processor: IOTAProcessor): GetNewAddressResponse = {
    try {
      val response = processor.api.generateNewAddresses(
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

    val response = createAddress(seed, securityLevel)(new IOTAProcessor(Namespace("iota")))

    logger.info("Your address is {}", response.getAddresses)

  }

}
