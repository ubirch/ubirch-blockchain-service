package com.ubirch.util

trait RunTimeHook {

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = shutdownHook()
  })

  def shutdownHook(): Unit

}
