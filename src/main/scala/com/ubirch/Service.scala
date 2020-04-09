package com.ubirch

import com.ubirch.models.WithExecutionContext
import com.ubirch.services.{Bucket, BucketPicker, Prometheus}

/**
  * Represents the assembled blockchain system
  */
object Service
  extends Bucket
  with BucketPicker
  with Prometheus
  with WithExecutionContext
