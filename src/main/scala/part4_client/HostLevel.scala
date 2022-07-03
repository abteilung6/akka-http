package part4_client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import scala.util.{Failure, Success}
import spray.json._

import java.util.UUID

object HostLevel extends App with PaymentJsonProtocol {

  implicit val system: ActorSystem = ActorSystem("HostLevel")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  val poolFlow = Http().cachedHostConnectionPool[Int]("www.google.com")

  Source(1 to 10)
    .map(i => (HttpRequest(), i))
    .via(poolFlow)
    .map {
      case (Success(response), value) =>
        // very important
        response.discardEntityBytes()
        s"Request $value has received response $response"
      case (Failure(exception), value) =>
        s"Request $value has failed with $exception"
    }
    //.runWith(Sink.foreach[String](println))

  import PaymentSystemDomain._
  val creditCards = List(
    CreditCard("4242-4242-4242-4242", "424", "tx-test-account"),
    CreditCard("1234-1234-1234-1234", "123", "tx-daniels-account"),
    CreditCard("4321-4321-1234-1234", "321", "tx-foo-account"),
  )

  val paymentRequest = creditCards.map(creditCard => PaymentRequest(creditCard, "akka-account", 99))
  val serverHttpRequests = paymentRequest.map(paymentRequest =>
    (
      HttpRequest(
        HttpMethods.POST,
        uri = Uri("/api/payments"),
        entity = HttpEntity(
          ContentTypes.`application/json`,
          paymentRequest.toJson.prettyPrint
        )
      ),
      UUID.randomUUID().toString
    )
  )

  Source(serverHttpRequests)
    .via(Http().cachedHostConnectionPool[String]("localhost", 8080))
    .runForeach { // (Try[HttpResponse], String)
      case (Success(response@HttpResponse(StatusCodes.Forbidden, _, _, _)), orderId) =>
        println(s"order id now allowed $response")
      case (Success(response), orderId) =>
        println(s"order id $orderId returned $response")
      case (Failure(exception), orderId) =>
        println(s"order $orderId failed with $exception")
    }

  // high volume, low latency requests
}
