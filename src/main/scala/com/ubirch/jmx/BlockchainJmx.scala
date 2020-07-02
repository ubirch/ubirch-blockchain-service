package com.ubirch.jmx

import java.math.BigInteger

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.BlockchainSystem.Namespace
import com.ubirch.services.ConsumptionCalc
import javax.management.{ InstanceAlreadyExistsException, ObjectName, StandardMBean }

/**
  * Represents a basic interface/trait for our blockchain jmx control
  */
trait BlockchainBean {
  def getBootGasPrice: String
  def getBootGasLimit: String
  def getCurrentGasPrice: String
  def getCurrentGasLimit: String

  def gasPrice(newGasPrice: String): Unit
  def gasLimit(newGasLimit: String): Unit
}

/**
  * Represents the JMX Implementation for our system.
  *
  * @param namespace Represents the namespace for the JMX, which is the ethereum name
  * @param consumptionCalc Represents an implementation of the consumption calculator.
  */
class BlockchainJmx(namespace: Namespace, consumptionCalc: ConsumptionCalc)
  extends JMXBase(new ObjectName(s"com.ubirch.services.blockchain:type=Balance,name=${namespace.value}")) with LazyLogging {

  def createBean(): Unit = {

    val mbean = new StandardMBean(classOf[BlockchainBean]) with BlockchainBean {
      override def getBootGasPrice: String = consumptionCalc.bootGasPrice.toString()
      override def getBootGasLimit: String = consumptionCalc.bootGasLimit.toString()
      override def getCurrentGasPrice: String = consumptionCalc.getCurrentGasPrice.toString()
      override def getCurrentGasLimit: String = consumptionCalc.getCurrentGasLimit.toString()
      override def gasPrice(newGasPrice: String): Unit = {
        logger.info("Setting new GasPrice={}", newGasPrice)
        consumptionCalc.clearWithGasPrice(new BigInteger(newGasPrice))
      }
      override def gasLimit(newGasLimit: String): Unit = {
        logger.info("Setting new GasLimit={}", newGasLimit)
        consumptionCalc.setCurrentGasLimit(new BigInteger(newGasLimit))
      }
    }

    try {
      mBeanServer.registerMBean(mbean, beanName)
      logger.info("Registered blockchain JMX MBean [{}]", beanName)
    } catch {
      case _: InstanceAlreadyExistsException =>
        logger.warn(s"Could not register Blockchain JMX MBean with name=$beanName as it is already registered. ")
    }

  }

}
