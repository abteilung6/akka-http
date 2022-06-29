package part3_highlevelserver

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.Future
import spray.json._
import part2_lowlevelserver.{Guitar, GuitarDB, GuitarStoreJsonProtocol}
import part2_lowlevelserver.GuitarDB.{CreateGuitar, FindAllGuitars, FindGuitar}




object HighLevelExample extends App with GuitarStoreJsonProtocol {

  implicit val system: ActorSystem = ActorSystem("LowLevelRest")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(2 seconds)
  import system.dispatcher

  val guitarDb = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = List(
    Guitar("fender", "stratorcaster"),
    Guitar("gibson", "les paul"),
    Guitar("martin", "lx1"),
  )
  guitarList.foreach { guitar =>
    guitarDb ! CreateGuitar(guitar)
  }

  val guitarServerRoute =
    path("api" / "guitar") {
      parameter('id.as[Int]) { (guitarId: Int) =>
        get {
          val guitarOptionFuture: Future[Option[Guitar]] = (guitarDb ? FindGuitar(guitarId)).mapTo[Option[Guitar]]
          val entityFuture = guitarOptionFuture.map { guitarOption =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitarOption.toJson.prettyPrint
            )
          }
          complete(entityFuture)
        }
      } ~
      get {
        val guitarFutures: Future[List[Guitar]] = (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
        val entityFuture = guitarFutures.map { guitars =>
          HttpEntity(
            ContentTypes.`application/json`,
            guitars.toJson.prettyPrint
          )
        }
        complete(entityFuture)
      }
    } ~
    path("api" / "guitar" / IntNumber) { guitarId =>
      get {
        val guitarOptionFuture: Future[Option[Guitar]] = (guitarDb ? FindGuitar(guitarId)).mapTo[Option[Guitar]]
        val entityFuture = guitarOptionFuture.map { guitarOption =>
          HttpEntity(
            ContentTypes.`application/json`,
            guitarOption.toJson.prettyPrint
          )
        }
        complete(entityFuture)
      }
    } ~
    path("api" / "guitar" / "inventory") {
      get {
        parameter('inStock.as[Boolean]) { inStock =>
          // ..
          complete(StatusCodes.OK)
        }
      }
    }

  def toHttpEntity(payload: String) = HttpEntity(ContentTypes.`application/json`, payload)

  val simplifiedGuitarServerRoute =
    (pathPrefix("api" / "guitar") & get) {
      path("inventory") {
        parameter('inStock.as[Boolean]) { inStock =>
          // ..
          complete(StatusCodes.OK)
        }
      } ~
      (path(IntNumber) | parameter('id.as[Int])) { guitarId =>
        val entityFuture = (guitarDb ? FindGuitar(guitarId))
          .mapTo[Option[Guitar]]
          .map(_.toJson.prettyPrint)
          .map(toHttpEntity)
        complete(entityFuture)
      } ~
      pathEndOrSingleSlash {
        val entityFuture = (guitarDb ? FindAllGuitars)
          .mapTo[List[Guitar]]
          .map(_.toJson.prettyPrint)
          .map(toHttpEntity)
        complete(entityFuture)
      }
    }

  Http().bindAndHandle(simplifiedGuitarServerRoute, "localhost", 8080)

}
