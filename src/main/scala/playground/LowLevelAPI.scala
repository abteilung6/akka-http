package playground

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object LowLevelAPI extends App {

  implicit val system: ActorSystem = ActorSystem("LowLevelAPI")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  val serverSource = Http().bind("localhost", 8000)
  val connectionSink = Sink.foreach[IncomingConnection] { connection =>
    println(s"Accepted incoming connection from: ${connection.remoteAddress}")
  }

  val serverBindFuture = serverSource.to(connectionSink).run()

  serverBindFuture.onComplete {
    case Success(binding) =>
      println("Server binding successful")
      // binding.unbind()
      // binding.terminate(2 seconds)
    case Failure(exception) => println(s"Server binding failed: ${exception.getMessage}")
  }

  /**
   * Method 1: synchronously serve HTTP responses
   */
  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, uri, headers, entity, protocol) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |  <body>
            |    hello
            |  </body>
            |</html>
            |""".stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |  <body>
            |    Not Found
            |  </body>
            |</html>
            |""".stripMargin
        )
      )
  }

  val httpSyncConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithSyncHandler(requestHandler)
  }
  // Http().bind("localhost", 8080).runWith(httpSyncConnectionHandler)
  // Http().bindAndHandleSync(requestHandler, "localhost", 8080)

  /**
   * Method 2: serve back HTTP responses asynchronously
   */
  val asyncRequestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), headers, entity, protocol) =>
      Future(HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |  <body>
            |    hello
            |  </body>
            |</html>
            |""".stripMargin
        )
      ))
    case request: HttpRequest =>
      request.discardEntityBytes()
      Future(HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |  <body>
            |    Not Found
            |  </body>
            |</html>
            |""".stripMargin
        )
      ))
  }

  val httpAsyncConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithAsyncHandler(asyncRequestHandler)
  }

  // streams-based "manual" version
  // Http().bind("localhost", 8081).runWith(httpAsyncConnectionHandler)
  // shorthand verison
  Http().bindAndHandleAsync(asyncRequestHandler, "localhost", 8081)

  /**
   * Method 3 - async via akka streams
   */
  val streamsBasedRequestHandler: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].map  {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), headers, entity, protocol) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |  <body>
            |    hello
            |  </body>
            |</html>
            |""".stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |  <body>
            |    Not Found
            |  </body>
            |</html>
            |""".stripMargin
        )
      )
  }

  // manual version
  Http().bind("localhost", 8082).runForeach { connection =>
    connection.handleWith(streamsBasedRequestHandler)
  }

  // shorthand
  // Http().bindAndHandle(streamsBasedRequestHandler, "localhost", 8082)
}
