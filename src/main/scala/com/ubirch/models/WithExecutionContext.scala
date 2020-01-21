package com.ubirch.models

import scala.concurrent.ExecutionContext

trait WithExecutionContext {

  implicit def ec: ExecutionContext = scala.concurrent.ExecutionContext.global

}
