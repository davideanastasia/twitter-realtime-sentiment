package com.da.webapp

import akka.actor._
import akka.routing.{Routee, RemoveRoutee, ActorRefRoutee, AddRoutee}
import akka.stream.actor.ActorPublisher
import org.apache.spark.{SparkContext, SparkConf}
import org.joda.time.DateTime

import org.json4s.JsonDSL._
import org.json4s.jackson.Serialization
import org.json4s.jackson.JsonMethods._


import scala.annotation.tailrec
import scala.concurrent.duration._

import com.datastax.spark.connector._

/**
  * for now a very simple actor, which keeps a separate buffer
  * for each subscriber. This could be rewritten to store the
  * vmstats in an actor somewhere centrally and pull them from there.
  *
  * Based on the standard publisher example from the akka docs.
  */
class TwitterDataPublisher(router: ActorRef) extends ActorPublisher[String] {

  case class QueueUpdated()

  import akka.stream.actor.ActorPublisherMessage._
  import scala.collection.mutable

  val MaxBufferSize = 50
  val queue = mutable.Queue[String]()

  var queueUpdated = false

  // on startup, register with routee
  override def preStart() {
    router ! AddRoutee(ActorRefRoutee(self))
  }

  // cleanly remove this actor from the router. To
  // make sure our custom router only keeps track of
  // alive actors.
  override def postStop(): Unit = {
    router ! RemoveRoutee(ActorRefRoutee(self))
  }

  def receive = {

    // receive new stats, add them to the queue, and quickly
    // exit.
    case stats: String  =>
      // remove the oldest one from the queue and add a new one
      if (queue.size == MaxBufferSize) queue.dequeue()
      queue += stats
      if (!queueUpdated) {
        queueUpdated = true
        self ! QueueUpdated
      }

    // we receive this message if there are new items in the
    // queue. If we have a demand for messages send the requested
    // demand.
    case QueueUpdated => deliver()

    // the connected subscriber request n messages, we don't need
    // to explicitely check the amount, we use totalDemand propery for this
    case Request(amount) =>
      deliver()

    // subscriber stops, so we stop ourselves.
    case Cancel =>
      context.stop(self)
  }

  /**
    * Deliver the message to the subscriber. In the case of websockets over TCP, note
    * that even if we have a slow consumer, we won't notice that immediately. First the
    * buffers will fill up before we get feedback.
    */
  @tailrec final def deliver(): Unit = {
    if (totalDemand == 0) {
      println(s"No more demand for: $this")
    }

    if (queue.isEmpty && totalDemand != 0) {
      // we can response to queue updated msgs again, since
      // we can't do anything until our queue contains stuff again.
      queueUpdated = false
    } else if (totalDemand > 0 && queue.nonEmpty) {
      onNext(queue.dequeue())
      deliver()
    }
  }
}

/**
  * Just a simple router, which collects some VM stats and sends them to the provided
  * actorRef each interval.
  */
class TwitterActor(router: ActorRef, delay: FiniteDuration, interval: FiniteDuration) extends Actor {

  implicit val formats = org.json4s.DefaultFormats
  import scala.concurrent.ExecutionContext.Implicits.global

  val conf = new SparkConf(false)
    .setMaster("local[*]")
    .setAppName("VMActor")
    .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    .set("spark.cassandra.connection.host", "127.0.0.1")

  val sc = new SparkContext(conf)
  val table = sc.cassandraTable("twitter", "twitter_data")

  context.system.scheduler.schedule(delay, interval) {
    val json = Serialization.write(getStats)

    router ! json
  }

  override def receive: Actor.Receive = {
    case _ => // just ignore any messages
  }

  def getStats = {  // : List[ Map[String, Any] ] =

    val data = table
      .select("feel", "weight")
//      .where("year = ?", "2015")
//      .where("month = ?", "12")
//      .where("day = ?", "6")
//      .where("hour >= ?", "16")
      .map(crw => (crw.getString("feel"), crw.getLong("weight")))
      .reduceByKey(_ + _)
      .sortBy(-_._2)

    val samples = data.map(_._2).cache()
    val sampleMin = samples.min
    val sampleMax = samples.max

    data
      .map{ case (k,v) => Map("text" -> k, "size" -> ((v.toDouble - sampleMin)/(sampleMax - sampleMin) * 100).toInt ) }
      .collect()
      .toList
  }
}

/**
  * Simple router where we can add and remove routee. This actor is not
  * immutable.
  */
class RouterActor extends Actor {
  var routees = Set[Routee]()

  def receive = {
    case ar: AddRoutee => routees = routees + ar.routee
    case rr: RemoveRoutee => routees = routees - rr.routee
    case msg => routees.foreach(_.send(msg, sender))
  }
}