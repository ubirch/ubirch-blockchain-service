package com.ubirch.services

import java.util.concurrent.atomic.AtomicReference

import com.typesafe.scalalogging.LazyLogging
import monix.execution.{ Cancelable, Scheduler }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

trait BalanceMonitor extends LazyLogging {

  implicit def ec: ExecutionContext

  object Balance {

    implicit lazy val scheduler: Scheduler = monix.execution.Scheduler(ec)

    private val balance = new AtomicReference[BigInt](-1)

    private def action(): Unit = {
      val newBalance = queryBalance
      registerNewBalance(newBalance)
      logger.info("local_balance={} incoming_balance={}", balance.get(), newBalance)
      balance.set(newBalance)
    }

    def start(every: FiniteDuration = 30 seconds): Cancelable =
      scheduler.scheduleWithFixedDelay(0 seconds, every)(action())

    def currentBalance: BigInt = balance.get()

  }

  def registerNewBalance(balance: BigInt): Unit

  def queryBalance: BigInt

}
