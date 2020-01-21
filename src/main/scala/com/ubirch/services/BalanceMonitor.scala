package com.ubirch.services

import java.util.concurrent.atomic.AtomicReference

import monix.execution.Scheduler

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

trait BalanceMonitor {

  private val balance = new AtomicReference[BigInt](0)

  implicit def ec: ExecutionContext

  implicit lazy val scheduler: Scheduler = monix.execution.Scheduler(ec)

  def start() = scheduler.scheduleWithFixedDelay(1 second, 2 seconds)()

}
