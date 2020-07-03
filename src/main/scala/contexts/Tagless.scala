package contexts

import cats.Monad
import cats.effect.{ContextShift, IO}
import cats.implicits._
import contexts.Tagless._

import scala.language.higherKinds
import scala.util.Random

object TaglessMain extends App {
  val api = new Api(new Dao)

  val apiCall = {
    implicit val appContext: AppContext[IO] = new AppContext[IO](Map("user" -> "1"))
    api.getRecipeForPublisher[IO](34)
  }

  apiCall.unsafeRunSync()
}

object Tagless {
  class AppContext[F[_]](value: Map[String, String]) {
    def get(): Map[String, String] = value
  }
  object AppContext {
    def apply[F[_]](implicit ev: AppContext[F]): AppContext[F] = ev
    def empty[F[_]] = new AppContext[F](Map.empty)
  }

  object Logger {
    def log(msg: String, mdc: Map[String, String]): Unit = {
      val thread = Thread.currentThread().getName
      println(s"$msg\nthread: $thread\nmdc: $mdc")
      println("=" * 50)
    }

    def logF[F[_]: Monad: AppContext](msg: String): F[Unit] =
      log(msg, AppContext[F].get()).pure[F]
  }

  class Api(dao: Dao) {
    def getRecipeForPublisher[F[_]: Monad: AppContext](id: Int): F[Recipe] = {
      for {
        publisher <- dao.getPublisher[F](id)
        recipeId = publisher.recipes.head
        recipe <- dao.getRecipe[F](recipeId)
        _ <- Logger.logF[F](s"The recipe: $recipe")
      } yield recipe
    }
  }

  class Dao {
    def getPublisher[F[_]: Monad: AppContext](id: Int): F[Publisher] = Monad[F].pure {
      Logger.log(s"Asked for publisher: $id", AppContext[F].get())
      Publisher(id, Seq(Random.nextInt(100)))
    }

    def getRecipe[F[_]: Monad: AppContext](id: Int): F[Recipe] = Monad[F].pure {
      Logger.log(s"Asked for recipe: $id", AppContext[F].get())
      Recipe(id)
    }
  }
}
