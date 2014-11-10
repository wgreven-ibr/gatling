package io.gatling.core.util.cache

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import io.gatling.core.config.GatlingConfiguration

import scala.collection.JavaConversions._
import scala.collection.concurrent

class ThreadSafeCache[K, V](name: String) {

  private var _enabled: Option[Boolean] = None
  private var _cache: Option[concurrent.Map[K, V]] = None

  def uninitializedException =
    throw new IllegalStateException(s"$name cache has not been initialized yet !")

  def initialize(cacheCapacityFunction: GatlingConfiguration => Long)(implicit config: GatlingConfiguration): Unit = {
    val capacity = cacheCapacityFunction(config)
    _enabled = Some(capacity > 0)
    _cache = {
      Some(
        new ConcurrentLinkedHashMap.Builder[K, V]
          .maximumWeightedCapacity(capacity)
          .build)
    }
  }

  def enabled = _enabled.getOrElse(uninitializedException)
  def cache = _cache.getOrElse(uninitializedException)

  def getOrElsePutIfAbsent(key: K, value: => V): V = cache.get(key) match {
    case Some(v) => v
    case None =>
      val v = value
      cache.putIfAbsent(key, v)
      v
  }
}
