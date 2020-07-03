package contexts

import contexts.Zio._
import zio.blocking.Blocking
import zio.{FiberRef, UIO, ZIO}

import scala.util.Random

object ZioMain extends App {

  val api = new Api(new Dao)

  val apiCall =
    for {
      _ <- MDCAdapter.local.locally(Map("user" -> "1")) {
        api.getRecipeForPublisher(34)
      }
      _ = Logger.log("Without context")
    } yield ()

  zio.Runtime.global.unsafeRun(apiCall)
}

object Zio {
  object MDCAdapter {
    val local: FiberRef[Map[String, String]] =
      zio.Runtime.default
        .unsafeRun(FiberRef.make[Map[String, String]](Map.empty))

    val localUnsafe: ThreadLocal[Map[String, String]] =
      zio.Runtime.default.unsafeRun(local.unsafeAsThreadLocal)
  }

  object Logger {
    def log(msg: String): Unit = {
      val mdc = MDCAdapter.localUnsafe.get()
      val thread = Thread.currentThread().getName
      println(s"$msg\nthread: $thread\nmdc: $mdc")
      println("=" * 50)
    }
  }

  class Api(dao: Dao) {
    def getRecipeForPublisher(id: Int): UIO[Recipe] = {
      for {
        publisher <- dao.getPublisher(id)
        recipeId <- ZIO.effectTotal(publisher.recipes.head)
        recipe <- dao.getRecipe(recipeId)
        _ = Logger.log(s"The recipe: $recipe")
      } yield recipe
    }
  }

  class Dao {
    def getPublisher(id: Int): UIO[Publisher] = {
      val result = zio.blocking.effectBlocking {
        Logger.log(s"Asked for publisher: $id")
        Publisher(id, Seq(Random.nextInt(100)))
      }
      result.provideLayer(Blocking.live).orDie
    }

    def getRecipe(id: Int): UIO[Recipe] = {
      val result = zio.blocking.effectBlocking {
        Logger.log(s"Asked for recipe: $id")
        Recipe(id)
      }
      result.provideLayer(Blocking.live).orDie
    }
  }
}
