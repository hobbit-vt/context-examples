package contexts

import contexts.Explicit._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.util.Random
import scala.concurrent.duration._

object ExplicitMain extends App {
  val api = new Api(new Dao)

  val apiCall =
    api.getRecipeForPublisher(34, Map("user" -> "1"))

  Await.result(apiCall, 1.minute)
}

object Explicit {

  object Logger {
    def log(msg: String, mdc: Map[String, String]): Unit = {
      val thread = Thread.currentThread().getName
      println(s"$msg\nthread: $thread\nmdc: $mdc")
      println("=" * 50)
    }
  }

  class Api(dao: Dao) {
    def getRecipeForPublisher(id: Int, context: Map[String, String]): Future[Recipe] = {
      for {
        publisher <- dao.getPublisher(id, context)
        recipeId <- Future.apply(publisher.recipes.head)
        recipe <- dao.getRecipe(recipeId, context)
        _ = Logger.log(s"The recipe: $recipe", context)
      } yield recipe
    }
  }

  class Dao {
    def getPublisher(id: Int, context: Map[String, String]): Future[Publisher] = {
      Future {
        Logger.log(s"Asked for publisher: $id", context)
        Publisher(id, Seq(Random.nextInt(100)))
      }
    }

    def getRecipe(id: Int, context: Map[String, String]): Future[Recipe] = {
      Future {
        Logger.log(s"Asked for recipe: $id", context)
        Recipe(id)
      }
    }
  }
}
