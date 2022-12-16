package com.ubirch.jmx

import com.ubirch.services.BlockchainSystem.Namespace
import com.ubirch.services.Flush

import com.typesafe.scalalogging.LazyLogging

import javax.management._

/**
  * Represents the definition for the Bucket Bean
  */
trait BucketPickerBean {
  def startFlush(): Unit
  def stopFlush(): Unit
}

/**
  * Represents the implementation of the Bucket Bean
  * @param namespace The namespace of this blockchain system
  * @param flush if messages should be discarded or not
  */
class BucketJmx(namespace: Namespace, flush: Flush)
  extends JMXBase(new ObjectName(s"com.ubirch.services.blockchain:type=BucketPicker,name=${namespace.value}")) with LazyLogging {

  def createBean(): Unit = {

    val mbean = new StandardMBean(classOf[BucketPickerBean]) with BucketPickerBean {

      override def startFlush(): Unit = {
        logger.info("Starting flushing")
        flush.setValue(true)
      }

      override def stopFlush(): Unit = {
        logger.info("Stopping flushing")
        flush.setValue(false)
      }

    }

    try {
      mBeanServer.registerMBean(mbean, beanName)
      logger.info("Registered BucketPicker JMX MBean [{}]", beanName)
    } catch {
      case _: InstanceAlreadyExistsException =>
        logger.warn(s"Could not register BucketPicker JMX MBean with name=$beanName as it is already registered. ")
    }

  }

}
