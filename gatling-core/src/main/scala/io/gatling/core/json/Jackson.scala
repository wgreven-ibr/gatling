/**
 * Copyright 2011-2014 eBusiness Information, Groupe Excilys (www.ebusinessinformation.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gatling.core.json

import java.io.{ InputStream, InputStreamReader }
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets._

import com.fasterxml.jackson.core.JsonParser.Feature
import com.fasterxml.jackson.databind.ObjectMapper
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.util.{ CharsetHelper, FastByteArrayInputStream }

object Jackson extends JsonParser {

  val JsonSupportedEncodings = Vector(UTF_8, UTF_16, CharsetHelper.UTF_32)

  private var _instance: Option[ObjectMapper] = None

  def initialize(implicit configuration: GatlingConfiguration): Unit = {
    val objectMapper = new ObjectMapper
    objectMapper.configure(Feature.ALLOW_COMMENTS, configuration.core.extract.jsonPath.jackson.allowComments)
    objectMapper.configure(Feature.ALLOW_SINGLE_QUOTES, configuration.core.extract.jsonPath.jackson.allowSingleQuotes)
    objectMapper.configure(Feature.ALLOW_UNQUOTED_FIELD_NAMES, configuration.core.extract.jsonPath.jackson.allowUnquotedFieldNames)
    _instance = Some(objectMapper)
  }

  def instance =
    _instance.getOrElse(throw new IllegalStateException("The ObjectMapper hasn't been initialized"))

  def parse(bytes: Array[Byte], charset: Charset) =
    if (JsonSupportedEncodings.contains(charset)) {
      instance.readValue(bytes, classOf[Object])
    } else {
      val reader = new InputStreamReader(new FastByteArrayInputStream(bytes), charset)
      instance.readValue(reader, classOf[Object])
    }

  def parse(string: String) = instance.readValue(string, classOf[Object])

  def parse(stream: InputStream, charset: Charset) =
    if (JsonSupportedEncodings.contains(charset)) {
      instance.readValue(stream, classOf[Object])
    } else {
      val reader = new InputStreamReader(stream, charset)
      instance.readValue(reader, classOf[Object])
    }

  def toJsonString(obj: Any) = instance.writeValueAsString(obj)
}
