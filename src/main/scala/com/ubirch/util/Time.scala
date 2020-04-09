package com.ubirch.util

/**
  * Represents a helper for timing executions
  */
object Time {

  case class Timed[R](result: R, t0: Long, t1: Long) {
    lazy val elapsed: Long = t1 - t0
    lazy val message: String = "Elapsed time: " + elapsed + "ns"
  }

  def time[R](block: => R): Timed[R] = {
    val t0 = System.nanoTime()
    val result = block // call-by-name
    val t1 = System.nanoTime()
    Timed(result, t0, t1)
  }

}
