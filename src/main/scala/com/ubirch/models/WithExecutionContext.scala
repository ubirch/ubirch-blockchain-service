package com.ubirch.models

import java.util.concurrent.Executors

import monix.execution.Scheduler

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }

object WithExecutionContext {
  lazy val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5))
  lazy val scheduler: Scheduler = monix.execution.Scheduler(ec)
}

trait WithExecutionContext {
  implicit def ec: ExecutionContextExecutor = WithExecutionContext.ec
  implicit def scheduler: Scheduler = WithExecutionContext.scheduler
}
