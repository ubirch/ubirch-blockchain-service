package com.ubirch.util

import com.ubirch.models.ResponseSerializer

/**
  * Represents a tool for json parsing
  */
object JsonSupport extends JsonHelper(List(new ResponseSerializer))
