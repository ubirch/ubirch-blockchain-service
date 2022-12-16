package com.ubirch.jmx

import com.typesafe.scalalogging.LazyLogging

import java.lang.management.ManagementFactory
import javax.management.{ InstanceNotFoundException, MBeanServer, ObjectName }

/**
  * Base class for creating JMX Monitoring systems
  * @param beanName Represents the name of the object
  */
abstract class JMXBase(private[jmx] val beanName: ObjectName) extends LazyLogging {

  private[jmx] val mBeanServer: MBeanServer = ManagementFactory.getPlatformMBeanServer

  def createBean(): Unit

  def unregisterMBean(): Unit = {
    try {
      mBeanServer.unregisterMBean(beanName)
    } catch {
      case _: InstanceNotFoundException =>
        logger.warn(s"Could not unregister Cluster JMX MBean with name=$beanName as it was not found.")
    }

  }

}
