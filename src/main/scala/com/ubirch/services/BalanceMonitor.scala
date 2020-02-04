package com.ubirch.services

import java.util.concurrent.atomic.AtomicReference

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.WithExecutionContext
import monix.execution.Cancelable

import scala.concurrent.duration._
import scala.language.postfixOps

trait BalanceMonitor extends WithExecutionContext with LazyLogging {

  object Balance {

    private val balance = new AtomicReference[BigInt](-1)

    private def action(): Unit = {
      val (address, newBalance) = queryBalance
      registerNewBalance(newBalance)
      logger.info("local_balance={} incoming_balance={} address={}", balance.get(), newBalance, address)
      balance.set(newBalance)
    }

    def start(every: FiniteDuration = 30 seconds): Cancelable =
      scheduler.scheduleWithFixedDelay(0 seconds, every)(action())

    def currentBalance: BigInt = balance.get()

  }

  def registerNewBalance(balance: BigInt): Unit

  def queryBalance: (String, BigInt)

}
