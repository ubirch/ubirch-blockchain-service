package com.ubirch.models

import io.prometheus.client.Gauge

trait Metrics {

  val balanceGauge = Gauge.build()
    .namespace("blockchain_anchoring")
    .name("balance")
    .help("Shows the current balance for the blockchain anchoring system")
    .labelNames("service")
    .register()

}
