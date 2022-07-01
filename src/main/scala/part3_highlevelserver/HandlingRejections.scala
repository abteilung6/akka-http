package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.util.Timeout
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MethodRejection, MissingQueryParamRejection, RejectionHandler, Route}

import scala.concurrent.duration._
import scala.language.postfixOps


object HandlingRejections {
  implicit val system: ActorSystem = ActorSystem("LowLevelRest")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(2 seconds)
  import system.dispatcher

  val simpleRoute: Route =
    path("api" / "myEndpoint") {
      get {
        complete(StatusCodes.OK)
      } ~
      parameter('id) { _ =>
        complete(StatusCodes.OK)
      }
    }

  val badRequestHandler: RejectionHandler = { rejections =>
    println(s"I have encountered rejections $rejections")
    Some(complete(StatusCodes.BadRequest))
  }

  val forbiddenHandler: RejectionHandler = { rejections =>
    println(s"I have encountered rejections $rejections")
    Some(complete(StatusCodes.Forbidden))
  }

  val simpleRouteWithHandlers: Route =
    handleRejections(badRequestHandler) {
      path("api" / "myEndpoint") {
        get {
          complete(StatusCodes.OK)
        } ~
        parameter('id) { _ =>
          complete(StatusCodes.OK)
        }
        post {
          handleRejections(forbiddenHandler) {
            parameter('myParam) { myParam =>
              complete(StatusCodes.OK)
            }
          }
        }
      }
    }

  implicit val customRejectionHandler: RejectionHandler = RejectionHandler.newBuilder()
    .handle {
      case m: MissingQueryParamRejection =>
        println(s"I got a query param rejection: $m")
        complete("Rejected query param!")
    }
    .handle {
      case m: MethodRejection =>
        println(s"I got a method rejection: $m")
        complete("Rejected method!")
    }
    .result()

  // sealing a route

  Http().bindAndHandle(simpleRoute, "localhost", 8080)
}
