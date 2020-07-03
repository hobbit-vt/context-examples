package contexts

import cats.Monad
import cats.data.Kleisli
import cats.implicits._
import contexts.ReaderT._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.higherKinds
import scala.util.Random

object ReaderTMain extends App {
  val api = new Api(new Dao)


  val apiCall =
    api.getRecipeForPublisher(34).run(Map("user" -> "1"))

  Await.result(apiCall, 1.minute)
}

object ReaderT {
  type AppContext = Map[String, String]

  object Logger {
    def log(msg: String, mdc: AppContext): Unit = {
      val thread = Thread.currentThread().getName
      println(s"$msg\nthread: $thread\nmdc: $mdc")
      println("=" * 50)
    }

    def logT[F[_]: Monad](msg: String): Kleisli[F, AppContext, Unit] = Kleisli { context =>
      log(msg, context).pure[F]
    }
  }

  class Api(dao: Dao) {
    def getRecipeForPublisher(id: Int): Kleisli[Future, AppContext, Recipe] = {
      for {
        publisher <- dao.getPublisher(id)
        recipeId = publisher.recipes.head
        recipe <- dao.getRecipe(recipeId)
         _ <- Logger.logT[Future](s"The recipe: $recipe")
      } yield recipe
    }
  }

  class Dao {
    def getPublisher(id: Int): Kleisli[Future, AppContext, Publisher] = Kleisli { context =>
      Future {
        Logger.log(s"Asked for publisher: $id", context)
        Publisher(id, Seq(Random.nextInt(100)))
      }
    }

    def getRecipe(id: Int): Kleisli[Future, AppContext, Recipe] = Kleisli { context =>
      Future {
        Logger.log(s"Asked for recipe: $id", context)
        Recipe(id)
      }
    }
  }
}
