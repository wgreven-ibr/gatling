package io.gatling.core.util.cache

import scala.collection.immutable.Queue

object Cache {
  def apply[K, V](maxCapacity: Int) = new Cache[K, V](Queue.empty, Map.empty, maxCapacity)
}

class Cache[K, V](queue: Queue[K], map: Map[K, V], maxCapacity: Int) {

  def +(kv: (K, V)): Cache[K, V] = {
    val (key, value) = kv
    add(key, value)
  }
  def add(key: K, value: V): Cache[K, V] = {
    if (map.contains(key))
      this

    else if (map.size == maxCapacity) {
      val (removedKey, newQueue) = queue.dequeue
      val newMap = map - removedKey + (key -> value)
      new Cache(newQueue.enqueue(key), newMap, maxCapacity)

    } else {
      val newQueue = queue.enqueue(key)
      val newMap = map + (key -> value)
      new Cache(newQueue, newMap, maxCapacity)
    }
  }

  def -(key: K): Cache[K, V] = remove(key)
  def remove(key: K): Cache[K, V] = {
    if (map.contains(key)) {
      val newQueue = queue.filter(_ != key)
      val newMap = map - key
      new Cache(newQueue, newMap, maxCapacity)

    } else
      this
  }

  def get(key: K): Option[V] = map.get(key)
}
