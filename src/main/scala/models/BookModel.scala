package models

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.internal.FatalError
import scala.util.{Failure, Success}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.StrictForm
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model.StatusCodes._

import models.BookModel.{Repo, schema}
import sangria.execution.{ErrorWithResolver, Executor, QueryAnalysisError, QueryReducer}
import sangria.ast.Document
import sangria.parser.QueryParser
import sangria.schema._
import shapeless.Id
import spray.json._

object BookModel {
  class Repo {
    private val Books = List(
      Book("1","Genesis","Drama","1"),
      Book("2","Exodus","Drama","1"),
      Book("3","Leveticus","Action","2"),
      Book("4","Numbers","Action","3"),
      Book("5","Deuteronomy","Comedy","3"),
      Book("6","Joshua","Drama","1")
    )

    private val Authors = List(
      Author("1","john",78),
      Author("2","sam",7),
      Author("3","dayo",8),
    )
    def getAuthor(id : String) = {
      Authors find (_.id == id)
    }

    def authors(lim : Option[Int]) = {
      lim match {
        case None =>  Authors
        case Some(num) => Authors.take(num)
      }
    }
    def authorsByAge(gAge : Option[Int])  =  gAge match {
      case None => Authors
      case Some(num) => Authors.filter(_.age > num)

    }

    def authorsByLimit(limit : Option[Int])  = limit match {
      case None =>  Authors
      case Some(num) => Authors.take(num)
    }

    def getBook(id : String) : Option[Book] =
      books find (_.id == id)

    def books : List[Book] = Books
  }
  case class Book (id : String, title : String ,genre : String , authorId :String )

  case class Author(id : String,name : String,age : Int)

  val AuthorType = ObjectType(
    "Author",
    fields = fields[Unit,Author](
      Field("id",StringType,resolve = _.value.id),
      Field("name",StringType,resolve = _.value.name),
      Field("age",IntType,resolve = _.value.age),
    )
  )
  val BookType = ObjectType(
    "Book",
    "Book model",
    fields = fields[Repo,Book](
      Field("id",StringType,resolve = _.value.id),
      Field("title",StringType,resolve = _.value.title),
      Field("genre",StringType,resolve = _.value.genre),
      Field("author",OptionType(AuthorType),resolve = c => c.ctx.getAuthor(c.value.id))

    )

  )

  //now let us define a query type

  /**
    * If an age is supplied, we filter on the age
    */

  val IdArgs = Argument("id",StringType)
  val authorAgeGreater = Argument("gAge",OptionInputType(IntType))
  val authorAgeLimit = Argument("limit",OptionInputType(IntType))
  val queryType = ObjectType(
    "Query",
    fields[Repo, Unit](
      Field("book", OptionType(BookType),
        description = Some("Returns a product with specific `id` . "),
        arguments = List(IdArgs),
        resolve = c => Future(c.ctx.getBook(c.args.arg("id")))
      ),
      Field("books", ListType(BookType),
        description = Some("Returns all books "),
        resolve = c => c.ctx.books),
    Field("authors", ListType(AuthorType),
      description = Some("Returns all Authors "),
      arguments = List(authorAgeLimit),
      resolve = c => c.ctx.authors(c.args.argOpt("limit")))
    )
  )





  val schema = Schema(queryType)


  import sangria.macros._
  val query =
    graphql"""
    query MyBooks {
      book(id: "2") {
        title
        genre
      }

      books {
        title
      }
    }
  """


}
object Server extends App with SprayJsonSupport with DefaultJsonProtocol {

  implicit val system = ActorSystem("sangria-server")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val route: Route =
    (post & path("graphql")) {
      entity(as[JsValue]) { requestJson ⇒
        println(requestJson)
        graphQLEndpoint(requestJson)
      }
    } ~
      get {
        getFromResource("graphiql.html")
      }

  Http().bindAndHandle(route, "0.0.0.0", 8080)

  def graphQLEndpoint(requestJson: JsValue) = {
    val JsObject(fields) = requestJson


    val JsString(query) = fields("query")


    val operation = fields.get("operationName") collect {
      case JsString(op) ⇒ op
    }

    val vars = fields.get("variables") match {
      case Some(obj: JsObject) ⇒ obj
      case _ ⇒ JsObject.empty
    }



    QueryParser.parse(query) match {

      // query parsed successfully, time to execute it!
      case scala.util.Success(queryAst) ⇒
        complete(executeGraphQLQuery(queryAst, operation, vars))

      // can't parse GraphQL query, return error
      case Failure(error) ⇒
        complete(BadRequest, JsObject("error" → JsString(error.getMessage)))
    }
  }
  import sangria.marshalling.sprayJson._

  def executeGraphQLQuery(query: Document, op: Option[String], vars: JsObject) = {
    Executor.execute(schema, query, new Repo, variables = vars, operationName = op,maxQueryDepth = Some(2),queryReducers = List(QueryReducer.rejectComplexQueries[Repo](200,
      (_,_) => new IllegalArgumentException("Query to Complex"))))
      .map(OK -> _)
      .recover {
        case error: QueryAnalysisError ⇒ BadRequest → error.resolveError
        case error: ErrorWithResolver ⇒ InternalServerError → error.resolveError
      }
  }

}
