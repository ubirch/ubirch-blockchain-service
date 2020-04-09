package com.ubirch.util

/**
  * Represents a tool for adding shutdown hooks
  */
trait RunTimeHook {

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = shutdownHook()
  })

  def shutdownHook(): Unit

}
