package com.ubirch.models

import io.prometheus.client.Gauge

trait BalanceGaugeMetric {

  val balanceGauge = Gauge.build()
    .name("current_balance")
    .help("Shows the current balance for the blockchain anchoring system")
    .labelNames("service")
    .register()

}
