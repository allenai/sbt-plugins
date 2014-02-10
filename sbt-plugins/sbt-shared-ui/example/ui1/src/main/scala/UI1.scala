import spray.routing.SimpleRoutingApp

import akka.actor._

object UI1 extends App with SimpleRoutingApp {

  implicit val system = ActorSystem("ui1")

  startServer(interface = "localhost", port = 8090) {
    pathEndOrSingleSlash {
      get {
        dynamic {
          println("Getting from resource")
          getFromFile("target/public/index.html")
        }
      }
    } ~
    path("assets" / Rest) { rest =>
      println(s"Getting from resource: $rest")
      getFromResource(s"public/$rest")
    } ~
    path("hi") {
      complete("you")
    }
  }

}
