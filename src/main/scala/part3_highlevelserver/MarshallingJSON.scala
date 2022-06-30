package part3_highlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout
import spray.json._

import scala.concurrent.duration._
import scala.language.postfixOps


case class Player(nickname: String, characterClass: String, level: Int)

object GameAreaMap {
  case object GetAllPlayers
  case class GetPlayer(nickname: String)
  case class GetPlayersByClass(characterClass: String)
  case class AddPlayer(player: Player)
  case class RemovePlayer(player: Player)
  case object OperationSuccess
}

class GameAreaMap extends Actor with ActorLogging {

  import GameAreaMap._

  var players: Map[String, Player] = Map[String, Player]()

  override def receive: Receive = {
    case GetAllPlayers =>
      log.info("Get all players")
      sender() ! players.values.toList
    case GetPlayer(nickname) =>
      log.info(s"Get play with nickname: $nickname")
      sender() ! players.get(nickname)
    case GetPlayersByClass(characterClass) =>
      log.info(s"Get players with class $characterClass")
      sender() ! players.values.toList.filter(_.characterClass == characterClass)
    case AddPlayer(player) =>
      log.info(s"Trying to add player $player")
      players = players + (player.nickname -> player)
      sender() ! OperationSuccess
    case RemovePlayer(player) =>
      log.info(s"Trying to remove player $player")
      players = players - player.nickname
      sender() ! OperationSuccess
  }
}

trait PlayerJsonProtocol extends DefaultJsonProtocol {
  implicit val playerFormat: RootJsonFormat[Player] = jsonFormat3(Player)
}

object MarshallingJSON extends App
  with PlayerJsonProtocol
  with SprayJsonSupport {
  implicit val system: ActorSystem = ActorSystem("MarshallingJSON")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(2 seconds)

  import system.dispatcher

  val gameAreaMapActor = system.actorOf(Props[GameAreaMap], "gameAreaMapActor")
  val playersList = List(
    Player("martin", "Warrior", 70),
    Player("roland", "Elf", 67),
    Player("daniel", "Wizard", 70),
  )

  import GameAreaMap._

  playersList.foreach { player =>
    gameAreaMapActor ! AddPlayer(player)
  }

  /**
   * - GET /api/player, returns all the players
   * - GET /api/player/(nickname), returns the player with the given name
   * - GET /api/player?nickname=X, does the same
   * - GET /api/player/class/(charClass), returns all the players with the given character class
   * - POST /api/player, add the player
   * - DELETE /api/player, removes player
   */

  val gameRoute =
    pathPrefix("api" / "player") {
      get {
        path("class" / Segment) { characterClass =>
          // get all the players with characterClass
          val playersByClassFuture = (gameAreaMapActor ? GetPlayersByClass(characterClass)).mapTo[List[Player]]
          complete(playersByClassFuture)
        } ~
          (path(Segment) | parameter('nickname)) { nickname =>
            // get the player with nickname
            val playerOptionFuture = (gameAreaMapActor ? GetPlayer(nickname)).mapTo[Option[Player]]
            complete(playerOptionFuture)
          } ~
          pathEndOrSingleSlash {
            complete((gameAreaMapActor ? GetAllPlayers).mapTo[List[Player]])
          }
      } ~
      post {
        entity(as[Player]) { player =>
          val operation = (gameAreaMapActor ? AddPlayer(player)).map(_ => StatusCodes.OK)
          complete(operation)
        }
        // add a player
      } ~
      delete {
        // delete the player
        entity(as[Player]) { player =>
          val operation = (gameAreaMapActor ? RemovePlayer(player)).map(_ => StatusCodes.OK)
          complete(operation)
        }
      }
    }

  Http().bindAndHandle(gameRoute, "localhost", 8080)
}
