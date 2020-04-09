package com.ubirch.models

import java.util.Date

import com.ubirch.util.JsonSupport._
import org.json4s.JsonDSL._
import org.json4s.{ CustomSerializer, JObject, MappingException }

import scala.util.control.NonFatal

/**
  * Represents the object used for in responses.
  * {
  *  'status':'added',
  *  'txid':txn_hash_str,
  *  'message':string,
  *  'blockchain':blockchain,
  *  'network_info':networkinfo,
  *  'network_type':networktype,
  *  'created':created
  * }
  * @param status Represents the status of the response
  * @param txId Represents the transaction id
  * @param message Represents a message in the response
  * @param blockchain Represents the blockchain in th response
  * @param networkInfo Represents the network info
  * @param networkType Represents the type of network
  * @param created Represents when the tx was created
  */
case class Response(status: String, txId: String, message: String, blockchain: String, networkInfo: String, networkType: String, created: Date) {
  def withCreated(newCreated: Date): Response = copy(created = newCreated)
}

/**
  * Represents a a companion object for the response which contains useful creation functions
  */
object Response {

  final val STATUS = "status"
  final val TX_ID = "txid"
  final val MESSAGE = "message"
  final val BLOCKCHAIN = "blockchain"
  final val NETWORK_INFO = "network_info"
  final val NETWORK_TYPE = "network_type"
  final val CREATED = "created"

  def Added(txId: String, message: String, blockchain: String, networkInfo: String, networkType: String): Response =
    new Response("added", txId, message, blockchain, networkInfo, networkType, new Date())

  def Timeout(txId: String, message: String, blockchain: String, networkInfo: String, networkType: String): Response =
    new Response("timeout", txId, message, blockchain, networkInfo, networkType, new Date())

  def Error(txId: String, message: String, blockchain: String, networkInfo: String, networkType: String): Response =
    new Response("error", txId, message, blockchain, networkInfo, networkType, new Date())

}

/**
  * Represents a customized serializer for the response object
  */
class ResponseSerializer extends CustomSerializer[Response](_ =>

  ({
    case jsonObj: JObject =>

      try {

        val status = (jsonObj \ Response.STATUS).extract[String]
        val txId = (jsonObj \ Response.TX_ID).extract[String]
        val message = (jsonObj \ Response.MESSAGE).extract[String]
        val blockchain = (jsonObj \ Response.BLOCKCHAIN).extract[String]
        val networkInfo = (jsonObj \ Response.NETWORK_INFO).extract[String]
        val networkType = (jsonObj \ Response.NETWORK_TYPE).extract[String]
        val created = (jsonObj \ Response.CREATED).extract[Date]

        Response(status, txId, message, blockchain, networkInfo, networkType, created)
      } catch {
        case NonFatal(e) =>
          val message = s"For Dates, Use this format: ${formats.dateFormat.format(new Date)} - ${e.getMessage}"
          throw MappingException(message, new java.lang.IllegalArgumentException(e))

      }

  }, {
    case res: Response =>
      (Response.STATUS -> res.status) ~
        (Response.TX_ID -> res.txId) ~
        (Response.MESSAGE -> res.message) ~
        (Response.BLOCKCHAIN -> res.blockchain) ~
        (Response.NETWORK_INFO -> res.networkInfo) ~
        (Response.NETWORK_TYPE -> res.networkType) ~
        (Response.CREATED -> formats.dateFormat.format(res.created))
  }))
