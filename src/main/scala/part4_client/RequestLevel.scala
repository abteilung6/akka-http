package part4_client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source

import scala.util.{Failure, Success}
import spray.json._

object RequestLevel extends App with PaymentJsonProtocol {
  implicit val system: ActorSystem = ActorSystem("RequestLevel")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  val responseFuture = Http().singleRequest(HttpRequest(uri = "http://www.google.com"))

  responseFuture.onComplete {
    case Success(response) =>
      // very important
      response.discardEntityBytes()
      println(s"The request was successful and returned: $response")
    case Failure(exception) =>
      println(s"The request failed with $exception")
  }

  import PaymentSystemDomain._
  val creditCards = List(
    CreditCard("4242-4242-4242-4242", "424", "tx-test-account"),
    CreditCard("1234-1234-1234-1234", "123", "tx-daniels-account"),
    CreditCard("4321-4321-1234-1234", "321", "tx-foo-account"),
  )

  val paymentRequest = creditCards.map(creditCard => PaymentRequest(creditCard, "akka-account", 99))
  val serverHttpRequests = paymentRequest.map(paymentRequest =>
    HttpRequest(
      HttpMethods.POST,
      uri = Uri("http://localhost:8080/api/payments"),
      entity = HttpEntity(
        ContentTypes.`application/json`,
        paymentRequest.toJson.prettyPrint
      )
    )
  )
  Source(serverHttpRequests)
    .mapAsyncUnordered(10)(request => Http().singleRequest(request))
    .runForeach(println)
}
