package com.ubirch.models

import io.prometheus.client.{ Counter, Gauge }

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

trait EtherumInternalMetrics {

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

