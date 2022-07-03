package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtSprayJson}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import java.util.concurrent.TimeUnit
import scala.util.{Failure, Success}


object SecurityDomain extends DefaultJsonProtocol{
  case class LoginRequest(username: String, password: String)
  implicit val loginRequestFormat: RootJsonFormat[LoginRequest] = jsonFormat2(LoginRequest)
}

object JwtAuthorization extends App with SprayJsonSupport {
  implicit val system: ActorSystem = ActorSystem("JwtAuthorization")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher
  import SecurityDomain._


  val superSecretPasswordDb = Map(
    "admin" -> "admin",
    "daniel" -> "rock",
  )

  val algorithm = JwtAlgorithm.HS256
  val secretKey = "secret"

  def checkPassword(username: String, password: String): Boolean =
    superSecretPasswordDb.contains(username) && superSecretPasswordDb(username) == password

  def createToken(username: String, expirationInDays: Int): String = {
    val claims = JwtClaim(
      expiration = Some(System.currentTimeMillis() / 1000 + TimeUnit.DAYS.toSeconds(expirationInDays)),
      issuedAt = Some(System.currentTimeMillis() / 1000),
      issuer = Some("akka")
    )

    JwtSprayJson.encode(claims, secretKey, algorithm) // jwt string
  }

  def isTokenExpired(token: String): Boolean = JwtSprayJson.decode(token, secretKey, Seq(algorithm)) match {
    case Success(claims) => claims.expiration.getOrElse(0L) < System.currentTimeMillis() / 1000
    case Failure(_) => true
  }

  def isTokenValid(token: String): Boolean = JwtSprayJson.isValid(token, secretKey, Seq(algorithm))


  val loginRoute =
    post {
      entity(as[LoginRequest]) {
        case LoginRequest(username, password) if checkPassword(username, password) =>
          val token = createToken(username, 1)
          respondWithHeader(RawHeader("Access-Token",  token)) {
            complete(StatusCodes.OK)
          }
        case _ => complete(StatusCodes.Unauthorized)
      }
    }

  val authenticatedRoute =
    (path("secureEndpoint") & get) {
      optionalHeaderValueByName("Authorization") {
        case Some(token) =>
          if (isTokenValid(token)) {
            if (isTokenExpired(token)) {
              complete(HttpResponse(status= StatusCodes.Unauthorized, entity = "Token is expired"))
            } else  {
              complete("User access authorized endpoint!")
            }
          } else {
            complete(HttpResponse(status= StatusCodes.Unauthorized, entity = "Token is invalid"))
          }
        case _ => complete(HttpResponse(status= StatusCodes.Unauthorized, entity = "No token provided"))
      }
    }

  val route = loginRoute ~ authenticatedRoute

  Http().bindAndHandle(route, "localhost", 8080)
}
