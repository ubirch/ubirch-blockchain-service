package com

import com.ubirch.models.ResponseSerializer
import org.json4s.jackson.Serialization
import org.json4s.{ Extraction, Formats, JValue, NoTypeHints, jackson }

package object ubirch {

  implicit val formats: Formats = Serialization.formats(NoTypeHints) ++ org.json4s.ext.JavaTypesSerializers.all ++ List(new ResponseSerializer)

  def toJValue[T: Manifest](v1: T): JValue = Extraction.decompose(v1)

  def stringify(v1: JValue, compact: Boolean = true): String =
    if (compact) jackson.compactJson(v1)
    else jackson.prettyJson(v1)

}
