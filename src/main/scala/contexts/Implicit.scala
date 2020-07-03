package contexts

import contexts.Implicit._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

object ImplicitMain extends App {
  val api = new Api(new Dao)

  val apiCall =
    api.getRecipeForPublisher(34)(Map("user" -> "1"))

  Await.result(apiCall, 1.minute)
}

object Implicit {
  object Logger {
    def log(msg: String)(implicit mdc: Map[String, String]): Unit = {
      val thread = Thread.currentThread().getName
      println(s"$msg\nthread: $thread\nmdc: $mdc")
      println("=" * 50)
    }
  }

  class Api(dao: Dao) {
    def getRecipeForPublisher(id: Int)(implicit context: Map[String, String]): Future[Recipe] = {
      for {
        publisher <- dao.getPublisher(id)
        recipeId <- Future.apply(publisher.recipes.head)
        recipe <- dao.getRecipe(recipeId)
        _ = Logger.log(s"The recipe: $recipe")
      } yield recipe
    }
  }

  class Dao {
    def getPublisher(id: Int)(implicit context: Map[String, String]): Future[Publisher] = {
      Future {
        Logger.log(s"Asked for publisher: $id")
        Publisher(id, Seq(Random.nextInt(100)))
      }
    }

    def getRecipe(id: Int)(implicit context: Map[String, String]): Future[Recipe] = {
      Future {
        Logger.log(s"Asked for recipe: $id")
        Recipe(id)
      }
    }
  }
}
