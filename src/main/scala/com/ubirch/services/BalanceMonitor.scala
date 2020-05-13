package com.ubirch.services

import java.lang.management.ManagementFactory
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicReference

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.WithExecutionContext
import com.ubirch.services.BlockchainSystem.Namespace
import javax.management.{ InstanceAlreadyExistsException, InstanceNotFoundException, ObjectName, StandardMBean }
import monix.execution.Cancelable

import scala.concurrent.duration._
import scala.language.postfixOps

trait BlockchainBean {
  def getBootGasPrice: String
  def getBootGasLimit: String
  def getCurrentGasPrice: String
  def getCurrentGasLimit: String

  def gasPrice(newGasPrice: String): Unit
  def gasLimit(newGasLimit: String): Unit
}

class ConsumptionCalc(val bootGasPrice: BigInt, val bootGasLimit: BigInt) {

  @volatile var currentGasPrice: BigInt = bootGasPrice
  @volatile var currentGasLimit: BigInt = bootGasLimit

  def setCurrentGasPrice(newGasPrice: BigInt): Unit = currentGasPrice = newGasPrice
  def setCurrentGasLimit(newGasLimit: BigInt): Unit = currentGasLimit = newGasLimit
  def calcGasValues: (BigInt, BigInt) = (currentGasPrice, currentGasLimit)

}

class BlockchainJmx(namespace: Namespace, consumptionCalc: ConsumptionCalc) extends LazyLogging {

  private val mBeanServer = ManagementFactory.getPlatformMBeanServer
  private val beanName = new ObjectName(s"com.ubirch.services.blockchain:type=Balance,name=${namespace.value}")

  def createBean(): Unit = {

    val mbean = new StandardMBean(classOf[BlockchainBean]) with BlockchainBean {
      override def getBootGasPrice: String = consumptionCalc.bootGasPrice.toString()
      override def getBootGasLimit: String = consumptionCalc.bootGasLimit.toString()
      override def getCurrentGasPrice: String = consumptionCalc.currentGasPrice.toString()
      override def getCurrentGasLimit: String = consumptionCalc.currentGasLimit.toString()
      override def gasPrice(newGasPrice: String): Unit = {
        logger.info("Setting new GasPrice={}", newGasPrice)
        consumptionCalc.setCurrentGasPrice(new BigInteger(newGasPrice))
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

  def unregisterMBean(): Unit = {
    try {
      mBeanServer.unregisterMBean(beanName)
    } catch {
      case _: InstanceNotFoundException =>
        logger.warn(s"Could not unregister Cluster JMX MBean with name=$beanName as it was not found.")
    }

  }

}

/**
  * Represents an internal service/component for monitoring the balances
  */
trait BalanceMonitor extends WithExecutionContext with LazyLogging {

  object Balance {

    private val balance = new AtomicReference[BigInt](-1)

    private def action(): Unit = try {
      val (address, newBalance) = queryBalance
      registerNewBalance(newBalance)
      val curr = balance.get()
      val diff = curr - newBalance
      logger.info("local_balance={} incoming_balance={} diff={} address={}", curr, newBalance, diff, address)
      balance.set(newBalance)
    } catch {
      case e: Exception =>
        logger.error("Error getting balance: " + e.getMessage, e)
    }

    def start(every: FiniteDuration = 30 seconds): Cancelable =
      scheduler.scheduleWithFixedDelay(0 seconds, every)(action())

    def currentBalance: BigInt = balance.get()

  }

  def registerNewBalance(balance: BigInt): Unit

  def queryBalance: (String, BigInt)

}
