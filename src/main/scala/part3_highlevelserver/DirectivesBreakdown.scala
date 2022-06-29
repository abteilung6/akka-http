package part3_highlevelserver

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, StatusCodes}
import akka.stream.ActorMaterializer

object DirectivesBreakdown extends App {

  implicit val system: ActorSystem = ActorSystem("DirectivesBreakdown")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._

  /**
   * Type #1: filtering directives
   */
  val simpleHttpMethodRoute =
    post {
      complete(StatusCodes.Forbidden)
    }

  val simplePathRoute =
    path("about") {
      complete(
        HttpEntity(
          ContentTypes.`application/json`,
          """
            |<html>
            |foo
            |</html>
            |""".stripMargin
        )
      )
    }

  val complexPathRoute =
    path("api" / "myEndpoint") {
      complete(StatusCodes.OK)
    }

  val dontConfuse =
    path("api/myEndpoint") { // not the same, url encoded
      complete(StatusCodes.OK)
    }

  val pathEndRoute =
    pathEndOrSingleSlash { // localhost:8080 or localhost:8080/
      complete(StatusCodes.OK)
    }

  // Http().bindAndHandle(complexPathRoute, "localhost", 8080)

  /**
   * Type #2: extraction directives
   *
   */
  // get on  /api/item/43
  val pathExtractionRoute = {
    path("api" / "item" / IntNumber) { (itemNumber: Int) =>
      // other directives

      println(s"I ve got a number in my path $itemNumber")
      complete(StatusCodes.OK)
    }
  }

  val pathMultiExtractionRoute = {
    path("api" / "item" / IntNumber / IntNumber) { (id, inventory) =>
      // other directives

      println(s"two numbers $id, $inventory")
      complete(StatusCodes.OK)
    }
  }

  val queryParamExtractionRoute = {
    // /api/item?id=45
    path("api" / "item") {
      parameter("id") { (itemId: String) =>
        println(s"extract itemId $itemId")
        complete(StatusCodes.OK)
      }
      parameter('foo.as[Int]) { (itemId: Int) =>
        println(s"extract itemId $itemId")
        complete(StatusCodes.OK)
      }
    }
  }

  val extractRequestRoute =
    path("controlEndpoint") {
      extractRequest { (httpRequest: HttpRequest) =>
        extractLog { (log: LoggingAdapter) =>
          println(s"request $httpRequest")
          complete(StatusCodes.OK)
        }
      }
    }

  /**
   * Type #3: composite directives
   */
  val simpleNestedRoute = {
    path("api" / "item") {
      get {
        complete(StatusCodes.OK)
      }
    }
  }

  val compactSimpleNestedRoute = (path("api" / "item") & get) {
    complete(StatusCodes.OK)
  }

  val compactExtractRequestRoute =
    (path("controlEndpoint") & extractRequest & extractLog) { (request, log) =>
      println(s"request $request")
      complete(StatusCodes.OK)
    }

  // /about and /aboutUs
  val repeatedRoute =
    path("about") {
      complete(StatusCodes.OK)
    } ~
    path("aboutUs") {
      complete(StatusCodes.OK)
    }

  // about AND aboutUs
  val dryRoute =
    (path("about") | path("aboutUs")) {
      complete(StatusCodes.OK)
    }

  // yourblog.com/42 AND yourblog.com?id=42
  val blogByIdRoute =
    path(IntNumber) { (blogId: Int) =>
      // complex server logic
      complete(StatusCodes.OK)
    }

  val blogByQueryParamRoute =
    parameter('postId.as[Int]) { (blogPostId: Int) =>
      // the same server logic
      complete(StatusCodes.OK)
    }

  val combinedBlogByIdRoute =
    (path(IntNumber) | parameter('postId.as[Int])) { (blogPostId: Int) =>
      complete(StatusCodes.OK)
    }

  /**
   * Type #4: "actionable" directives
   */

  val completeOkRoute = complete(StatusCodes.OK)

  val failedRoute =
    path("notSupported") {
      failWith(new RuntimeException("Unsupported!")) // completes with HTTP 500
    }

  // reject first route and checks for another route match
  val routeWithRejection =
    path("home") {
      reject
    } ~
    path("index") {
      completeOkRoute
    }



  Http().bindAndHandle(compactSimpleNestedRoute, "localhost", 8080)

}
