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
package io.gatling.recorder.controller

import java.nio.file.Path
import java.security.Security
import java.util.concurrent.ConcurrentLinkedQueue

import com.ning.http.client.uri.Uri
import io.gatling.recorder.http.handler.remote.TimedHttpRequest
import org.bouncycastle.jce.provider.BouncyCastleProvider

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.duration.DurationLong

import org.jboss.netty.handler.codec.http.{ HttpRequest, HttpResponse }
import org.jboss.netty.handler.codec.http.HttpHeaders.Names.PROXY_AUTHORIZATION

import com.ning.http.util.Base64
import com.typesafe.scalalogging.StrictLogging

import io.gatling.core.validation.{ Failure, Success }
import io.gatling.core.util.PathHelper._
import io.gatling.recorder.{ Har, Proxy }
import io.gatling.recorder.config.RecorderConfiguration
import io.gatling.recorder.config.RecorderPropertiesBuilder
import io.gatling.recorder.http.HttpProxy
import io.gatling.recorder.scenario._
import io.gatling.recorder.ui._

object RecorderController {
  def apply(props: mutable.Map[String, Any], recorderConfigFile: Option[Path] = None): Unit = {
    RecorderConfiguration.initialSetup(props, recorderConfigFile)
    new RecorderController
    Security.addProvider(new BouncyCastleProvider)
  }
}

class RecorderController(implicit configuration: RecorderConfiguration) extends StrictLogging {

  private val frontEnd = RecorderFrontend.newFrontend(this)

  @volatile private var proxy: HttpProxy = _

  // Collection of tuples, (arrivalTime, request)
  private val currentRequests = new ConcurrentLinkedQueue[TimedScenarioElement[RequestElement]]()
  // Collection of tuples, (arrivalTime, tag)
  private val currentTags = new ConcurrentLinkedQueue[TimedScenarioElement[TagElement]]()

  frontEnd.init()

  def startRecording(): Unit = {
    val selectedMode = frontEnd.selectedMode
    val harFilePath = frontEnd.harFilePath
    if (selectedMode == Har && !string2path(harFilePath).exists) {
      frontEnd.handleMissingHarFile(harFilePath)
    } else {
      val simulationFile = ScenarioExporter.simulationFilePath
      val proceed = if (simulationFile.exists) frontEnd.askSimulationOverwrite else true
      if (proceed) {
        selectedMode match {
          case Har =>
            ScenarioExporter.exportScenario(harFilePath) match {
              case Failure(errMsg) => frontEnd.handleHarExportFailure(errMsg)
              case Success(_)      => frontEnd.handleHarExportSuccess()
            }
          case Proxy =>
            proxy = new HttpProxy(this)
            frontEnd.recordingStarted()
        }
      }
    }
  }

  def stopRecording(save: Boolean): Unit = {
    frontEnd.recordingStopped()
    try {
      if (currentRequests.isEmpty)
        logger.info("Nothing was recorded, skipping scenario generation")
      else {
        val scenario = ScenarioDefinition(currentRequests.toVector, currentTags.toVector)
        ScenarioExporter.saveScenario(scenario)
      }

    } finally {
      proxy.shutdown()
      clearRecorderState()
      frontEnd.init()
    }
  }

  def receiveRequest(request: HttpRequest): Unit =
    // TODO NICO - that's not the appropriate place to synchronize !
    synchronized {
      // If Outgoing Proxy set, we record the credentials to use them when sending the request
      Option(request.headers.get(PROXY_AUTHORIZATION)).foreach {
        header =>
          // Split on " " and take 2nd group (Basic credentialsInBase64==)
          val credentials = new String(Base64.decode(header.split(" ")(1))).split(":")
          val props = new RecorderPropertiesBuilder
          props.proxyUsername(credentials(0))
          props.proxyPassword(credentials(1))
          RecorderConfiguration.reload(props.build)
      }
    }

  def receiveResponse(request: TimedHttpRequest, response: HttpResponse): Unit = {
    if (configuration.filters.filters.map(_.accept(request.httpRequest.getUri)).getOrElse(true)) {
      val arrivalTime = System.currentTimeMillis

      val requestEl = RequestElement(request.httpRequest, response)
      currentRequests.add(TimedScenarioElement(request.sendTime, arrivalTime, requestEl))

      // Notify the frontend
      val previousSendTime = currentRequests.lastOption.map(_.sendTime)
      previousSendTime.foreach { t =>
        val delta = (arrivalTime - t).milliseconds
        if (delta > configuration.core.thresholdForPauseCreation)
          frontEnd.receiveEventInfo(PauseInfo(delta))
      }
      frontEnd.receiveEventInfo(RequestInfo(request.httpRequest, response))
    }
  }

  def addTag(text: String): Unit = {
    val now = System.currentTimeMillis
    currentTags.add(TimedScenarioElement(now, now, TagElement(text)))
    frontEnd.receiveEventInfo(TagInfo(text))
  }

  def secureConnection(securedHostURI: Uri): Unit = {
    frontEnd.receiveEventInfo(SSLInfo(securedHostURI.toUrl))
  }

  def clearRecorderState(): Unit = {
    currentRequests.clear()
    currentTags.clear()
  }
}
