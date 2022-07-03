package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.CompactByteString

import scala.language.postfixOps


object WebsocketsDemo extends App {
  implicit val system: ActorSystem = ActorSystem("HandlingExceptions")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  /*
  Message has two subtypes: TextMessage and BinaryMessage
   */

  val textMessage = TextMessage(Source.single("hello via a text message")) // wrapper over akka source
  val binaryMessage = BinaryMessage(Source.single(CompactByteString("Hello via a binary message")))

  def websocketFlow: Flow[Message, Message, Any] = Flow[Message].map {
    case textMessage: TextMessage =>
      TextMessage(Source.single("Server says back:") ++ textMessage.textStream ++ Source.single("!"))
    case binaryMessage: BinaryMessage =>
      binaryMessage.dataStream.runWith(Sink.ignore)
      TextMessage(Source.single("Server received binary message..."))
  }

  val route =
    path("greeter") {
      handleWebSocketMessages(websocketFlow)
    } ~
    path("social") {
      handleWebSocketMessages(socialFlow)
    }

  Http().bindAndHandle(route, "localhost", 8080)

  case class SocialPost(owner: String, content: String)

  val socialFeed = Source(
    List(
      SocialPost("Martin", "Scala 3 has been announced"),
      SocialPost("Daniel", "what ever"),
      SocialPost("Martin", "lalalal")
    )
  )

  import scala.concurrent.duration._
  val socialMessages = socialFeed
    .throttle(1, 2 seconds)
    .map(socialPost => TextMessage(s"${socialPost.owner} said: ${socialPost.content}"))

  val socialFlow: Flow[Message, Message, Any] = Flow.fromSinkAndSource(
    Sink.foreach[Message](println),
    socialMessages
  )
}
