package com.ubirch.services

import com.ubirch.models.WithExecutionContext

import com.typesafe.scalalogging.LazyLogging
import monix.execution.Cancelable

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Represents an internal service/component for monitoring the balances
  */
trait BalanceMonitor extends WithExecutionContext with LazyLogging {

  object Balance {

    private val balance = new AtomicReference[BigInt](-1)

    private def action(): Unit = try {
      val (address, newBalance) = queryBalance
      registerNewBalance(newBalance)
      val curr = balance.get()
      val diff = curr - newBalance
      logger.info("status=OK[balance_update] local_balance={} incoming_balance={} diff={} address={}", curr, newBalance, diff, address)
      balance.set(newBalance)
    } catch {
      case e: Exception =>
        logger.error("Error getting balance: " + e.getMessage, e)
    }

    def start(every: FiniteDuration = 30 seconds): Cancelable =
      scheduler.scheduleWithFixedDelay(0 seconds, every)(action())

    def currentBalance: BigInt = balance.get()

  }

  def registerNewBalance(balance: BigInt): Unit

  def queryBalance: (String, BigInt)

}
