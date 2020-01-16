package com.ubirch.models

import java.util.Date

import com.ubirch.util.JsonHelper
import org.json4s.JsonDSL._
import org.json4s.{ CustomSerializer, JObject, MappingException }

import scala.util.control.NonFatal

//return {'status': 'added', 'txid': txn_hash_str, 'message': string, 'blockchain': blockchain, 'network_info': networkinfo, 'network_type': networktype, 'created': created}

object CustomSerializers {
  val all = List(new ResponseSerializer)
}

object BlockchainJsonSupport extends JsonHelper(CustomSerializers.all)

import BlockchainJsonSupport._

object ResponseSerializer {
  val STATUS = "status"
  val TX_ID = "txid"
  val TXN_HASH_STR = "txn_hash_str"
  val MESSAGE = "message"
  val BLOCKCHAIN = "blockchain"
  val NETWORK_INFO = "network_info"
  val NETWORK_TYPE = "network_type"
  val CREATED = "created"
}

class ResponseSerializer extends CustomSerializer[Response](_ =>

  ({
    case jsonObj: JObject =>
      import ResponseSerializer._

      try {

        val status = (jsonObj \ STATUS).extract[String]
        val txId = (jsonObj \ TX_ID).extract[String]
        val message = (jsonObj \ MESSAGE).extract[String]
        val blockchain = (jsonObj \ BLOCKCHAIN).extract[String]
        val networkInfo = (jsonObj \ NETWORK_INFO).extract[String]
        val networkType = (jsonObj \ NETWORK_TYPE).extract[String]
        val created = (jsonObj \ CREATED).extract[Date]

        Response(status, txId, message, blockchain, networkInfo, networkType, created)
      } catch {
        case NonFatal(e) =>
          val message = s"For Dates, Use this format: ${formats.dateFormat.format(new Date)} - ${e.getMessage}"
          throw MappingException(message, new java.lang.IllegalArgumentException(e))

      }

  }, {
    case res: Response =>
      import ResponseSerializer._
      (STATUS -> res.status) ~
        (TX_ID -> res.txId) ~
        (MESSAGE -> res.message) ~
        (BLOCKCHAIN -> res.blockchain) ~
        (NETWORK_INFO -> res.networkInfo) ~
        (NETWORK_TYPE -> res.networkType) ~
        (CREATED -> formats.dateFormat.format(res.created))
  }))
