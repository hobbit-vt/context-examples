package contexts

import com.twitter.util.{Await, Future, FuturePool, FutureTask, Local}
import contexts.Twitter._

import scala.util.Random

object TwitterMain extends App {
  val api = new Api(new Dao)

  val apiCall =
    for {
      _ <- MDCAdapter.local.let(Map("user" -> "1")) {
        api.getRecipeForPublisher(34)
      }
      _ = Logger.log("Without context")
    } yield ()

  Await.result(apiCall)
}

object Twitter {
  object MDCAdapter {
    val local = new Local[Map[String, String]]
  }

  object Logger {
    def log(msg: String): Unit = {
      val mdc = MDCAdapter.local().getOrElse(Map.empty)
      val thread = Thread.currentThread().getName
      println(s"$msg\nthread: $thread\nmdc: $mdc")
      println("=" * 50)
    }
  }

  class Api(dao: Dao) {
    def getRecipeForPublisher(id: Int): Future[Recipe] = {
      for {
        publisher <- dao.getPublisher(id)
        recipeId <- Future.value(publisher.recipes.head)
        recipe <- dao.getRecipe(recipeId)
        _ = Logger.log(s"The recipe: $recipe")
      } yield recipe
    }
  }

  class Dao {
    def getPublisher(id: Int): Future[Publisher] = {
      FuturePool.unboundedPool {
        Logger.log(s"Asked for publisher: $id")
        Publisher(id, Seq(Random.nextInt(100)))
      }
    }

    def getRecipe(id: Int): Future[Recipe] = {
      FuturePool.unboundedPool {
        Logger.log(s"Asked for recipe: $id")
        Recipe(id)
      }
    }
  }
}
