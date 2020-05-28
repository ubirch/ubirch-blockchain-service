package com.ubirch.services

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

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
  * Basic abstraction for a consumption calc
  */
trait ConsumptionCalc {
  val bootGasPrice: BigInt
  val bootGasLimit: BigInt

  var currentGasPrice: BigInt = bootGasPrice
  var currentGasLimit: BigInt = bootGasLimit

  val windowSize: Int

  //stats points
  val duration: DescriptiveStatistics = new DescriptiveStatistics
  val price: DescriptiveStatistics = new DescriptiveStatistics
  val limit: DescriptiveStatistics = new DescriptiveStatistics
  val usedDelta: DescriptiveStatistics = new DescriptiveStatistics

  def calcGasValues(td: Double = 55000000000L.toDouble, tu: Double = .85): (BigInt, BigInt)

  def addStatistics(calculationPoint: StatsData): Unit = synchronized {
    duration.addValue(calculationPoint.duration)
    price.addValue(calculationPoint.price.toDouble)
    limit.addValue(calculationPoint.limit.toDouble)
    usedDelta.addValue(calculationPoint.usedDelta)
  }

  def setCurrentGasLimit(newGasLimit: BigInt): Unit = currentGasLimit = synchronized(newGasLimit)

  def setCurrentGasPrice(newGasPrice: BigInt): Unit = currentGasPrice = synchronized(newGasPrice)

  def clearWithGasPrice(newGasPrice: BigInt): Unit = synchronized {
    duration.clear()
    price.clear()
    limit.clear()
    usedDelta.clear()
    currentGasPrice = newGasPrice
  }

  def setWindows(windowSize: Int): Unit = {
    duration.setWindowSize(windowSize)
    price.setWindowSize(windowSize)
    limit.setWindowSize(windowSize)
    usedDelta.setWindowSize(windowSize)
  }

}

/**
  * Represents a calculator aimed calculating the next possible
  * gas price
  *
  * @param bootGasPrice Represents the gas price with which the system starts off
  * @param bootGasLimit Represents the gas limit with with the system starts off
  * @param windowSize Represents how many values will be taken into account for
  *                   calculating the statistics.
  */
class PersistentConsumptionCalc(
    val bootGasPrice: BigInt,
    val bootGasLimit: BigInt,
    val windowSize: Int = 10,
    stepUpPercentage: Double = 110,
    stepDownPercentage: Double = 30,
    stepDownPercentageAFT: Double = 90,
    maxStepsDownAFT: Int = 3
) extends ConsumptionCalc {

  setWindows(windowSize)

  //calc funcs
  private val calcPer: Double => Double => Double = percentage => price => (price * percentage) / 100
  private val asBigInt: Double => BigInt = double => BigDecimal(double).toBigInt()
  private val stepUp: Double => BigInt = calcPer(stepUpPercentage) andThen asBigInt
  private val stepDown: Double => BigInt = calcPer(stepDownPercentage) andThen asBigInt
  private val stepDownAFT: Double => BigInt = calcPer(stepDownPercentageAFT) andThen asBigInt

  private var lastGood: Double = -1
  private var lastStepDownAFT: Double = maxStepsDownAFT

  def calcGasValues(td: Double = 55000000000L.toDouble, tu: Double = .85): (BigInt, BigInt) = {
    val size = (duration.getN - 1).toInt
    if (size > 0) {
      val gpm = price.getGeometricMean
      val dn = duration.getElement(size)

      if ((dn > td) && usedDelta.getGeometricMean <= tu) {
        //step up
        val pn_1 = price.getElement(size - 1)
        lastGood = pn_1
        setCurrentGasPrice(stepUp(gpm))
      } else if ((lastGood > -1) && lastStepDownAFT > 0) {
        //This is step downs after first timeout
        lastStepDownAFT = lastStepDownAFT - 1
        setCurrentGasPrice(stepDownAFT(lastGood))
      } else if (lastGood > -1) {
        //steady
        setCurrentGasPrice(asBigInt(lastGood))
      } else {
        //step down
        setCurrentGasPrice(stepDown(gpm))
      }
    }

    (currentGasPrice, currentGasLimit)
  }

}

class ConservativeConsumptionCalc(
    val bootGasPrice: BigInt,
    val bootGasLimit: BigInt,
    val windowSize: Int = 10,
    stepUpPercentage: Double = 110,
    stepDownPercentage: Double = 30
) extends ConsumptionCalc {

  setWindows(windowSize)

  //calc funcs
  private val calcPer: Double => Double => Double = percentage => price => (price * percentage) / 100
  private val asBigInt: Double => BigInt = double => BigDecimal(double).toBigInt()
  private val stepUp: Double => BigInt = calcPer(stepUpPercentage) andThen asBigInt
  private val stepDown: Double => BigInt = calcPer(stepDownPercentage) andThen asBigInt

  private var lastGood: Double = -1

  def calcGasValues(td: Double = 50000000000L.toDouble, tu: Double = .85): (BigInt, BigInt) = {
    val size = (duration.getN - 1).toInt

    if (size > 0) {
      val gpm = price.getGeometricMean
      val dn = duration.getElement(size)

      if ((dn > td) && usedDelta.getGeometricMean <= tu) {
        val pn_1 = price.getElement(size - 1)
        lastGood = pn_1
        setCurrentGasPrice(stepUp(gpm))
      } else if (lastGood > -1) {
        setCurrentGasPrice(asBigInt(lastGood))
      } else {
        setCurrentGasPrice(stepDown(gpm))
      }
    }

    (currentGasPrice, currentGasLimit)
  }

}
