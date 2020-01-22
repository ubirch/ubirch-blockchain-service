package com.ubirch.models

import io.prometheus.client.Counter

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
