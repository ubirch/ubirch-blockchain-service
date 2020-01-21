package com.ubirch

import com.ubirch.services.{ Bucket, BucketPicker, Prometheus }

object Service
  extends Bucket
  with BucketPicker
  with Prometheus {

}
