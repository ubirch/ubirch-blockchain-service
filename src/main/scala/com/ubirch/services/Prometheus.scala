package com.ubirch.services

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.express.ConfigBase
import com.ubirch.kafka.metrics.PrometheusMetricsHelper
import com.ubirch.util.RunTimeHook
import io.prometheus.client.exporter.HTTPServer

trait Prometheus extends RunTimeHook with LazyLogging {

  trait PrometheusMetrics extends ConfigBase {

    val port: Int = conf.getInt("blockchainAnchoring.metrics.prometheus.port")

    logger.debug("Creating Prometheus Server on Port[{}]", port)

    val server: HTTPServer = PrometheusMetricsHelper.create(port)

  }

  val prometheusServer: PrometheusMetrics = new PrometheusMetrics {}

  override def shutdownHook(): Unit = {
    logger.info("Shutting down Prometheus")
    prometheusServer.server.stop()
  }

}

