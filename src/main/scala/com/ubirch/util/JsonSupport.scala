package com.ubirch.util

import com.ubirch.models.ResponseSerializer

object JsonSupport extends JsonHelper(List(new ResponseSerializer))
