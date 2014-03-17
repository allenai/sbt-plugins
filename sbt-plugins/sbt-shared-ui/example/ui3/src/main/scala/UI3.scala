import spray.routing.SimpleRoutingApp

import akka.actor._

object UI3 extends App with SimpleRoutingApp {

  implicit val system = ActorSystem("ui3")

  startServer(interface = "localhost", port = 8093) {
    pathEndOrSingleSlash {
      get {
        getFromResource(s"web/public/main/ui3/html/index.html")
      }
    } ~
    path("ui1") {
      get {
        getFromResource(s"web/public/main/ui1/html/index.html")
      }
    } ~
    path("assets" / Rest) { rest =>
      getFromResource(s"web/public/main/$rest")
    }
  }

}
