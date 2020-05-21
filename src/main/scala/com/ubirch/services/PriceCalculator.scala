package com.ubirch.services

import java.lang.management.ManagementFactory
import java.math.BigInteger

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.BlockchainSystem.Namespace
import javax.management.{ InstanceAlreadyExistsException, InstanceNotFoundException, ObjectName, StandardMBean }
import org.apache.commons.math3.stat.descriptive.{ DescriptiveStatistics, SynchronizedDescriptiveStatistics }

import scala.language.postfixOps

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
  * Represents a data structure that allows easy packing for the basic
  * statistics points
  * @param duration Represents the duration in nano seconds of the tx.
  * @param price Represents the transaction fee
  * @param limit Represents the max limit to respect
  * @param usedDelta represents the % of the unit price and the limit
  */
case class StatsData(
    duration: Long,
    price: BigInt,
    limit: BigInt,
    unit: BigInt,
    usedDelta: Double
)

/**
  * Represents a calculator aimed calculating the next possible
  * gas price
  *
  * @param bootGasPrice Represents the gas price with which the system starts off
  * @param bootGasLimit Represents the gas limit with with the system starts off
  * @param windowSize Represents how many values will be taken into account for
  *                   calculating the statistics.
  */
class ConsumptionCalc(val bootGasPrice: BigInt, val bootGasLimit: BigInt, windowSize: Int = 10, stepUpPercentage: Double = 110, stepDownPercentage: Double = 30) {

  @volatile var currentGasPrice: BigInt = bootGasPrice
  @volatile var currentGasLimit: BigInt = bootGasLimit

  val duration: DescriptiveStatistics = new SynchronizedDescriptiveStatistics
  duration.setWindowSize(windowSize)
  val price: DescriptiveStatistics = new SynchronizedDescriptiveStatistics
  price.setWindowSize(windowSize)
  val limit: DescriptiveStatistics = new SynchronizedDescriptiveStatistics
  limit.setWindowSize(windowSize)
  val usedDelta: DescriptiveStatistics = new SynchronizedDescriptiveStatistics
  usedDelta.setWindowSize(windowSize)

  def addStatistics(calculationPoint: StatsData): Unit = {
    duration.addValue(calculationPoint.duration)
    price.addValue(calculationPoint.price.toDouble)
    limit.addValue(calculationPoint.limit.toDouble)
    usedDelta.addValue(calculationPoint.usedDelta)
  }

  def clearWithGasPrice(newGasPrice: BigInt): Unit = {
    duration.clear()
    price.clear()
    limit.clear()
    usedDelta.clear()
    currentGasPrice = newGasPrice
  }

  def setCurrentGasPrice(newGasPrice: BigInt): Unit = currentGasPrice = newGasPrice

  def setCurrentGasLimit(newGasLimit: BigInt): Unit = currentGasLimit = newGasLimit

  val stepUp: Double => Double = price => (price * stepUpPercentage) / 100
  val stepDown: Double => Double = price => (price * stepDownPercentage) / 100
  val asBigInt: Double => BigInt = double => BigDecimal(double).toBigInt()
  val goUp: Double => BigInt = stepUp andThen asBigInt
  val goDown: Double => BigInt = stepDown andThen asBigInt

  private var lg: Double = -1

  def calcGasValues(td: Double = 55000000000L.toDouble, tu: Double = .85): (BigInt, BigInt) = {
    val size = (duration.getN - 1).toInt

    if (size > 0) {
      val gpm = price.getGeometricMean
      val dn = duration.getElement(size)

      if ((dn > td) && usedDelta.getGeometricMean <= tu) {
        val pn_1 = price.getElement(size - 1)
        lg = pn_1
        setCurrentGasPrice(goUp(gpm))
      } else if (lg > -1) {
        setCurrentGasPrice(asBigInt(lg))
      } else {
        setCurrentGasPrice(goDown(gpm))
      }
    }

    (currentGasPrice, currentGasLimit)
  }

}

/**
  * Represents the JMX Implementation for our system.
  * @param namespace Represents the namespace for the JMX, which is the ethereum name
  * @param consumptionCalc Represents an implementation of the consumption calculator.
  */
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

  def unregisterMBean(): Unit = {
    try {
      mBeanServer.unregisterMBean(beanName)
    } catch {
      case _: InstanceNotFoundException =>
        logger.warn(s"Could not unregister Cluster JMX MBean with name=$beanName as it was not found.")
    }

  }

}
