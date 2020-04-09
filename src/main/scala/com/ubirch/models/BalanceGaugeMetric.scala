package com.ubirch.models

import io.prometheus.client.Gauge

/**
  * Represents a Prometheus gauge for the balance.
  * It is only used in blockchains that need this kind of metric
  */
trait BalanceGaugeMetric {

  val balanceGauge = Gauge.build()
    .name("current_balance")
    .help("Shows the current balance for the blockchain anchoring system")
    .labelNames("service")
    .register()

}
