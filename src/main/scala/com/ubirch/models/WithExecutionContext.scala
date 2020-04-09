package com.ubirch.models

import java.util.concurrent.Executors

import monix.execution.Scheduler

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

/**
  * Represents a companion object that serves as singleton for mix-ins
  */
object WithExecutionContext {
  lazy val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5))
  lazy val scheduler: Scheduler = monix.execution.Scheduler(ec)
}

/**
  * Represents the execution and scheduler contexts for async execution
  */
trait WithExecutionContext {
  implicit def ec: ExecutionContextExecutor = WithExecutionContext.ec
  implicit def scheduler: Scheduler = WithExecutionContext.scheduler
}
