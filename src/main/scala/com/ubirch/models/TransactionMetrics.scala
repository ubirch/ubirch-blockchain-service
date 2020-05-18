package com.ubirch.models

import io.prometheus.client.{ Counter, Gauge }

/**
  * Represents Transaction-related Prometheus Metrics
  */
trait TransactionMetrics {

  val successCounter = Counter.build()
    .name("transaction_success")
    .help("Represents the number of transaction successes")
    .labelNames("service")
    .register()

  val errorCounter = Counter.build()
    .name("transaction_failures")
    .help("Represents the number of transaction failures")
    .labelNames("service")
    .register()

}

/**
  * Represents Time-related Prometheus Metrics
  */
trait TimeMetrics {

  val txTimeGauge = Gauge.build()
    .name("time_used")
    .help("Represents the time used when getting a valid response")
    .labelNames("service")
    .register()

}

/**
  * Represents Ethereum-specific Metrics
  */
trait EthereumInternalMetrics {

  val txFeeGauge = Gauge.build()
    .name("tx_fee")
    .help("Represents the transaction fee")
    .labelNames("service")
    .register()

  val gasPriceGauge = Gauge.build()
    .name("gas_price")
    .help("Represents the gas price")
    .labelNames("service")
    .register()

  val gasLimitGauge = Gauge.build()
    .name("gas_limit")
    .help("Represents the gas limit")
    .labelNames("service")
    .register()

  val gasUsedGauge = Gauge.build()
    .name("gas_used")
    .help("Represents the gas used")
    .labelNames("service")
    .register()

  val usedDeltaGauge = Gauge.build()
    .name("used_delta")
    .help("Represents the used delta")
    .labelNames("service")
    .register()

}

