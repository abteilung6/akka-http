package part4_client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import scala.util.{Failure, Success}
import spray.json._

object ConnectionLevel extends App with PaymentJsonProtocol {
  implicit val system: ActorSystem = ActorSystem("ConnectionLevel")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  val connectionFlow = Http().outgoingConnection("www.google.com")

  def oneOffRequest(request: HttpRequest) =
    Source.single(request).via(connectionFlow).runWith(Sink.head)

  oneOffRequest(HttpRequest()).onComplete {
    case Success(response) => println(s"Successful response $response")
    case Failure(exception) => println(s"sending the request failed $exception")
  }

  /*
  A small payments systems
   */

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
      uri = Uri("/api/payments"),
      entity = HttpEntity(
        ContentTypes.`application/json`,
        paymentRequest.toJson.prettyPrint
      )
    )
  )
  Source(serverHttpRequests)
    .via(Http().outgoingConnection("localhost", 8080))
    .to(Sink.foreach[HttpResponse](println))
    .run()
}
