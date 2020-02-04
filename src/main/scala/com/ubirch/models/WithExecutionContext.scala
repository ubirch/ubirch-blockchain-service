package com.ubirch.models

import java.util.concurrent.Executors

import monix.execution.Scheduler

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }

trait WithExecutionContext {

  implicit lazy val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5))

  implicit lazy val scheduler: Scheduler = monix.execution.Scheduler(ec)

}
