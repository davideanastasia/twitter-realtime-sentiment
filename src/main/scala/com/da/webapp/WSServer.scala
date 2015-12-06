package com.da.webapp

import java.util.concurrent.TimeoutException

import akka.actor.{Props, ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.{Uri, HttpResponse, HttpRequest}
import akka.http.scaladsl.model.ws.{UpgradeToWebsocket, Message, TextMessage}
import akka.stream.scaladsl.{Merge, Source, FlowGraph, Flow}
import akka.stream.ActorMaterializer

import scala.concurrent.duration._

import scala.concurrent.Await

object WSRequest {

  def unapply(req: HttpRequest) : Option[HttpRequest] = {
    if (req.header[UpgradeToWebsocket].isDefined) {
      req.header[UpgradeToWebsocket] match {
        case Some(upgrade) => Some(req)
        case None => None
      }
    } else None
  }

}

object WSServer extends App {

  // required actorsystem and flow materializer
  implicit val system = ActorSystem("websockets")
  implicit val fm = ActorMaterializer()

  // setup the actors for the stats
  // router: will keep a list of connected actorpublisher, to inform them about new stats.
  // vmactor: will start sending messages to the router, which will pass them on to any
  // connected routee
  val router: ActorRef = system.actorOf(Props[RouterActor], "router")
  val vmactor: ActorRef = system.actorOf(Props(classOf[TwitterActor], router, 0 seconds, 10 seconds))

  // Bind to an HTTP port and handle incoming messages.
  // With the custom extractor we're always certain the header contains
  // the correct upgrade message.
  // We can pass in a socketoptions to tune the buffer behavior
  // e.g options =  List(Inet.SO.SendBufferSize(100))
  val binding = Http().bindAndHandleSync({

//    case WSRequest(req@HttpRequest(GET, Uri.Path("/simple"), _, _, _)) => handleWith(req, Flows.reverseFlow)
//    case WSRequest(req@HttpRequest(GET, Uri.Path("/echo"), _, _, _)) => handleWith(req, Flows.echoFlow)
//    case WSRequest(req@HttpRequest(GET, Uri.Path("/graph"), _, _, _)) => handleWith(req, Flows.graphFlow)
//    case WSRequest(req@HttpRequest(GET, Uri.Path("/graphWithSource"), _, _, _)) => handleWith(req, Flows.graphFlowWithExtraSource)
    case WSRequest(req@HttpRequest(GET, Uri.Path("/stats"), _, _, _)) => handleWith(req, Flows.graphFlowWithStats(router))
    case _: HttpRequest => HttpResponse(400, entity = "Invalid websocket request")

  }, interface = "localhost", port = 9001)



  // binding is a future, we assume it's ready within a second or timeout
  try {
    Await.result(binding, 1 second)
    println("Server online at http://localhost:9001")
  } catch {
    case exc: TimeoutException =>
      println("Server took to long to startup, shutting down")
      system.shutdown()
  }

  /**
    * Simple helper function, that connects a flow to a specific websocket upgrade request
    */
  def handleWith(req: HttpRequest, flow: Flow[Message, Message, Unit]) = req.header[UpgradeToWebsocket].get.handleMessages(flow)

}

object Flows {

  def graphFlowWithStats(router: ActorRef): Flow[Message, Message, Unit] = {
    Flow() { implicit b =>
      import FlowGraph.Implicits._

      // create an actor source
      val source = Source.actorPublisher[String](Props(classOf[TwitterDataPublisher], router))

      // Graph elements we'll use
      val merge = b.add(Merge[String](2))
      val filter = b.add(Flow[String].filter(_ => false))

      // convert to int so we can connect to merge
      val mapMsgToString = b.add(Flow[Message].map[String] { msg => "" })
      val mapStringToMsg = b.add(Flow[String].map[Message]( x => TextMessage.Strict(x)))

      val statsSource = b.add(source)

      // connect the graph
      mapMsgToString ~> filter ~> merge // this part of the merge will never provide msgs
      statsSource ~> merge ~> mapStringToMsg

      // expose ports
      (mapMsgToString.inlet, mapStringToMsg.outlet)
    }
  }

}
