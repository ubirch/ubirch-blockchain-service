package com.ubirch.models

import java.util.Date

//return {'status': 'added', 'txid': txn_hash_str, 'message': string, 'blockchain': blockchain, 'network_info': networkinfo, 'network_type': networktype, 'created': created}
case class Response(status: String, txId: String, message: String, blockchain: String, networkInfo: String, networkType: String, created: Date)

object Response {
  def Added(txId: String, message: String, blockchain: String, networkInfo: String, networkType: String): Response =
    new Response("added", txId, message, blockchain, networkInfo, networkType, new Date())

  def Timeout(txId: String, message: String, blockchain: String, networkInfo: String, networkType: String): Response =
    new Response("timeout", txId, message, blockchain, networkInfo, networkType, new Date())

  def Error(txId: String, message: String, blockchain: String, networkInfo: String, networkType: String): Response =
    new Response("error", txId, message, blockchain, networkInfo, networkType, new Date())

}
