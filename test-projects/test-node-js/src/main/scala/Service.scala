import akka.actor._

import spray.routing._
import spray.routing.directives.CachingDirectives
import spray.can.server.Stats
import spray.can.Http
import spray.httpx.SprayJsonSupport
import spray.httpx.encoding.Gzip
import spray.util._
import spray.http._
import MediaTypes._
import CachingDirectives._

class ServiceActor extends Actor with HttpService {

  def actorRefFactory = context

  def receive = runRoute(route)

  implicit def executionContext = actorRefFactory.dispatcher

  val route =
    pathEndOrSingleSlash {
      getFromFile("public/index.html")
    } ~
    path(Rest) { rest =>
      getFromFile(s"public/${rest}")
    }
}
