package com.ubirch.services

import java.lang.management.ManagementFactory
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicReference

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.WithExecutionContext
import com.ubirch.services.BlockchainSystem.Namespace
import javax.management.{ InstanceAlreadyExistsException, InstanceNotFoundException, ObjectName, StandardMBean }
import monix.execution.Cancelable
import org.apache.commons.math3.stat.descriptive.{ DescriptiveStatistics, SynchronizedDescriptiveStatistics }

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

case class CalculationData(
    duration: Long,
    payedFee: BigInt,
    price: BigInt,
    limit: BigInt,
    unit: BigInt,
    usedDelta: Double
)

class ConsumptionCalc(val bootGasPrice: BigInt, val bootGasLimit: BigInt, windowSize: Int = 10) {

  @volatile var currentGasPrice: BigInt = bootGasPrice
  @volatile var currentGasLimit: BigInt = bootGasLimit

  val duration: DescriptiveStatistics = new SynchronizedDescriptiveStatistics
  val payedFee: DescriptiveStatistics = new SynchronizedDescriptiveStatistics
  val price: DescriptiveStatistics = new SynchronizedDescriptiveStatistics
  val limit: DescriptiveStatistics = new SynchronizedDescriptiveStatistics
  val unit: DescriptiveStatistics = new SynchronizedDescriptiveStatistics
  val usedDelta: DescriptiveStatistics = new SynchronizedDescriptiveStatistics

  duration.setWindowSize(windowSize)
  payedFee.setWindowSize(windowSize)
  limit.setWindowSize(windowSize)
  unit.setWindowSize(windowSize)
  usedDelta.setWindowSize(windowSize)

  def addStatistics(calculationPoint: CalculationData): Unit = {
    duration.addValue(calculationPoint.duration)
    payedFee.addValue(calculationPoint.payedFee.toDouble)
    price.addValue(calculationPoint.price.toDouble)
    limit.addValue(calculationPoint.limit.toDouble)
    unit.addValue(calculationPoint.unit.toDouble)
    usedDelta.addValue(calculationPoint.usedDelta)
  }

  def clearWithGasPrice(newGasPrice: BigInt): Unit = {
    duration.clear()
    payedFee.clear()
    price.clear()
    limit.clear()
    unit.clear()
    usedDelta.clear()
    currentGasPrice = newGasPrice
  }

  def setCurrentGasPrice(newGasPrice: BigInt): Unit = currentGasPrice = newGasPrice

  def setCurrentGasLimit(newGasLimit: BigInt): Unit = currentGasLimit = newGasLimit

  val stepUp: Double => Double = price => (price * 110) / 100
  val stepDown: Double => Double = price => (price * 30) / 100
  val asBigInt: Double => BigInt = double => BigDecimal(double).toBigInt()
  val goUp = stepUp andThen asBigInt
  val goDown = stepDown andThen asBigInt

  def calcGasValues: (BigInt, BigInt) = {
    val td = 50000000000L.toDouble
    val tu = .85
    val dn = duration.getN

    if (dn > 0) {
      val gpm = price.getGeometricMean
      val d = duration.getElement(dn.toInt - 1)

      if ((d > td) && usedDelta.getGeometricMean <= tu) {
        setCurrentGasPrice(goUp(gpm))
      } else {
        setCurrentGasPrice(goDown(gpm))
      }
    }

    (currentGasPrice, currentGasLimit)
  }

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
