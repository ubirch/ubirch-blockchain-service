package com.ubirch.services

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.express.ConfigBase
import com.ubirch.kafka.metrics.PrometheusMetricsHelper
import io.prometheus.client.exporter.HTTPServer

/**
  * Represents a Prometheus Server.
  */
trait Prometheus extends LazyLogging {

  trait PrometheusMetrics extends ConfigBase {

    val port: Int = conf.getInt(ConfPaths.PROMETHEUS_PORT)

    logger.debug("Creating Prometheus Server on Port[{}]", port)

    val server: HTTPServer = PrometheusMetricsHelper.default(port)

  }

  val prometheusServer: PrometheusMetrics = new PrometheusMetrics {}

  sys.addShutdownHook {
    logger.info("Shutting down Prometheus")
    prometheusServer.server.stop()
  }

}

