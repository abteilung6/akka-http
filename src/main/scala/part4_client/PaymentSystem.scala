package part4_client

import akka.pattern.ask
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import part4_client.PaymentSystemDomain.{PaymentAccepted, PaymentRejected, PaymentRequest}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.duration._
import scala.language.postfixOps


case class CreditCard(serialNumber: String, securityCode: String, account: String)

object PaymentSystemDomain {
  case class PaymentRequest(creditCard: CreditCard, receiverAccount: String, amount: Double) // dont use double for money in prod
  case object PaymentAccepted
  case object PaymentRejected
}

trait PaymentJsonProtocol extends DefaultJsonProtocol {
  implicit val creditCardFormat: RootJsonFormat[CreditCard] = jsonFormat3(CreditCard)
  implicit val paymentRequestFormat: RootJsonFormat[PaymentRequest] = jsonFormat3(PaymentRequest)
}

class PaymentValidator extends Actor with ActorLogging {

  import PaymentSystemDomain._

  override def receive: Receive = {
    case PaymentRequest(CreditCard(serialNumber, _, senderAccount), receiverAccount, amount) =>
      log.info(s"$senderAccount is trying to send $amount dollars to $receiverAccount")
      if (serialNumber == "1234-1234-1234-1234") sender() ! PaymentRejected
      else sender() ! PaymentAccepted
  }
}

object PaymentSystem extends App with PaymentJsonProtocol with SprayJsonSupport {

  // microservice for payments
  implicit val system: ActorSystem = ActorSystem("PaymentSystem")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  val paymentValidator = system.actorOf(Props[PaymentValidator], "paymentValidator")
  implicit val timeout: Timeout = Timeout(2 seconds)

  val paymentRoute =
    path("api" / "payments") {
      post {
        entity(as[PaymentRequest]) { paymentRequest =>
          val validationResponseFuture = (paymentValidator ? paymentRequest).map {
            case PaymentRejected => StatusCodes.Forbidden
            case PaymentAccepted => StatusCodes.OK
            case _ => StatusCodes.BadRequest
          }
          complete(validationResponseFuture)
        }
      }
    }

  Http().bindAndHandle(paymentRoute, "localhost", 8080)
}
