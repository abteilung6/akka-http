package part3_highlevelserver

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MethodRejection, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpec}
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

case class Book(id: Int, author: String, title: String)
trait BookJsonProtocol extends DefaultJsonProtocol {
  implicit val bookFormat: RootJsonFormat[Book] = jsonFormat3(Book)
}

class RouteDSLSpec extends WordSpec with Matchers with ScalatestRouteTest with BookJsonProtocol {
  import RouteDSLSpec._

  "A digital library backend" should {
    "return all books in the library" in {
      // send an http request through an endpoint that you want to test
      // inspect the response
      Get("/api/book") ~> libraryRoute ~> check {
        // assertions
        status shouldBe StatusCodes.OK
        entityAs[List[Book]] shouldBe books
      }
    }

    "return a book by hitting the query parameter endpoint" in {
      Get("/api/book?id=2") ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Option[Book]] shouldBe Some(Book(2, "tolkien", "The lord of the rings"))
      }
    }

    "return a book by calling the endpoint with the id in the path" in {
      Get("/api/book/2") ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK
        val strictEntityFuture = response.entity.toStrict(1 second)
        val strictEntity = Await.result(strictEntityFuture, 1 second)

        strictEntity.contentType shouldBe ContentTypes.`application/json`
        val book = strictEntity.data.utf8String.parseJson.convertTo[Option[Book]]
        book shouldBe Some(Book(2, "tolkien", "The lord of the rings"))
      }
    }

    "insert a book into the database" in {
      val newBook = Book(5, "foo", "bar")
      Post("/api/book", newBook) ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK
        assert(books.contains(newBook))
        books should contain(newBook)
      }
    }

    "not accept other methods then POST and GET" in {
      Delete("/api/book") ~> libraryRoute ~> check {
        rejections should not be empty
        rejections.should(not).be(empty)

        val methodRejections = rejections.collect {
          case rejection: MethodRejection => rejection
        }

        methodRejections.length shouldBe 2
      }
    }
  }
}

object RouteDSLSpec extends BookJsonProtocol with SprayJsonSupport{
  // code under test
  var books = List(
    Book(1, "Harper Lee", "To kill a mocking bird"),
    Book(2, "tolkien", "The lord of the rings"),
    Book(3, "marting", "A song of ice and fire"),
    Book(4, "robbins", "Awaken of the giant within"),
  )

  /*
  Get /api/book - all books
  GET /api/book/X - return a single book with id X
  GET /api/book?id=X
  POST /api/book - adds a book
   */

  val libraryRoute: Route =
    pathPrefix("api" / "book") {
      get {
        (path(IntNumber) | parameter('id.as[Int])) { id =>
          complete(books.find(_.id == id))
        } ~
        pathEndOrSingleSlash {
          complete(books)
        }
      } ~
      post {
        entity(as[Book]) { book =>
          books = books :+ book
          complete(StatusCodes.OK)
        } ~
        complete(StatusCodes.BadRequest)
      }
    }
}