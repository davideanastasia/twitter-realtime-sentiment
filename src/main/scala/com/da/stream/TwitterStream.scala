package com.da.stream

import java.io.FileInputStream
import java.util.Properties

import com.datastax.spark.connector.streaming._
import com.datastax.spark.connector.writer.{TTLOption, WriteConf}
import org.apache.log4j.{Level, Logger}
import org.apache.spark.SparkConf
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.joda.time.DateTime

import scala.collection.mutable

object TwitterStream {

  Logger.getLogger("org.apache.spark").setLevel(Level.WARN)
  Logger.getLogger("org.apache.spark.storage.BlockManager").setLevel(Level.ERROR)

  def configureTwitterCredentials(apiKey: String, apiSecret: String, accessToken: String, accessTokenSecret: String) {
    val configs = new mutable.HashMap[String, String] ++= Seq(
      "apiKey" -> apiKey, "apiSecret" -> apiSecret, "accessToken" -> accessToken, "accessTokenSecret" -> accessTokenSecret)
    println("Configuring Twitter OAuth")
    configs.foreach{ case(key, value) =>
      if (value.trim.isEmpty) {
        throw new Exception("Error setting authentication - value for " + key + " not set")
      }
      val fullKey = "twitter4j.oauth." + key.replace("api", "consumer")
      System.setProperty(fullKey, value.trim)
      println("\tProperty " + fullKey + " set as [" + value.trim + "]")
    }
    println()
  }

  def configureTwitterCredentials(path: String) {
    val prop = new Properties()
    prop.load(new FileInputStream(path))

    configureTwitterCredentials(
      prop.getProperty("consumerKey"), prop.getProperty("consumerSecret"),
      prop.getProperty("accessToken"), prop.getProperty("accessTokenSecret")
    )
  }

  case class DatedFeel(feel: String, year: Int, month: Int, day: Int, hour: Int)

  // <main class> twitter.properties feelings.dict.csv

  def main (args: Array[String]) {

    configureTwitterCredentials(args(0))

    val conf = new SparkConf()
      .setAppName("TwitterStream")
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .set("spark.cassandra.connection.host", "127.0.0.1")
      .setMaster("local[*]")

    val ssc = new StreamingContext(conf, Seconds(10))
    ssc.checkpoint("/tmp/checkpoint")

    val sc = ssc.sparkContext

    val feelings = sc.textFile(args(1)).map(item => {
      val t = item.split(",", -1)

      (t(0).toLowerCase, t(1).toLowerCase)
    }).collect().toMap

    println(feelings)

    val feelingsBc = sc.broadcast(feelings)

    val tweets = TwitterUtils.createStream(ssc, None)

//    val statuses = tweets
//      .flatMap(status => {
//        Option(status.getGeoLocation) match {
//          case Some(g) => Some(s"${g.getLatitude},${g.getLongitude}")
//          case None => None
//        }
//      })

    val statuses = tweets.flatMap(i => {
      val date = new DateTime(i.getCreatedAt)
      val tokens = i.getText.split(" ", -1)

      tokens.map( DatedFeel(_, date.getYear, date.getMonthOfYear, date.getDayOfMonth, date.getHourOfDay))
    })
    val words = statuses
      .map {
        case x: DatedFeel if x.feel.startsWith("#") =>
          DatedFeel(x.feel.substring(1).toLowerCase, x.year, x.month, x.day, x.hour)
        case x: DatedFeel =>
          DatedFeel(x.feel.toLowerCase, x.year, x.month, x.day, x.hour)
      }
    .filter { case x: DatedFeel => !x.feel.isEmpty }

    val counts = words
      .flatMap(x => {
        feelingsBc.value.get(x.feel) match {
          case Some(feel) => Some(DatedFeel(feel, x.year, x.month, x.day, x.hour))
          case None => None
        }
      })
      .map(w => (w, 1))
      .reduceByKeyAndWindow(_ + _, _ - _, Seconds(60), Seconds(10))
      .reduceByKey(_ + _)

    counts
      .map { case (k,v) => (k.feel, v) }
      .saveToCassandra("twitter", "twitter_data", writeConf = WriteConf(ttl = TTLOption.constant(300)))

    ssc.start()
    ssc.awaitTermination()
  }
}
