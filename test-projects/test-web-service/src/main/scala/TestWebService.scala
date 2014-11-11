package org.allenai.sbt.webservice.test

import akka.actor.{ Actor, ActorSystem, Props }
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import spray.can.Http
import spray.routing.HttpServiceActor

import scala.concurrent.duration._

object TestWebService extends App {

  implicit val system = ActorSystem("sbt-web-service-tester")

  // implicit ExecutionContext
  import system.dispatcher

  implicit val timeout = Timeout(5.seconds)

  val service = system.actorOf(Props(classOf[TestWebServiceActor]), "service")

  IO(Http) ? Http.Bind(service, interface = "0.0.0.0", port = 8888)
}

class TestWebServiceActor extends HttpServiceActor {
  override def receive = runRoute(route)

  val route = {
    get {
      complete("OK")
    }
  }
}
