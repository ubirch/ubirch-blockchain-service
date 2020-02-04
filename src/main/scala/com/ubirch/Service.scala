package com.ubirch

import com.ubirch.models.WithExecutionContext
import com.ubirch.services.{ Bucket, BucketPicker, Prometheus }

object Service
  extends Bucket
  with BucketPicker
  with Prometheus
  with WithExecutionContext
