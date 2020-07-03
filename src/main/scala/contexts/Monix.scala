package contexts

import contexts.Monix._
import monix.eval.{Task, TaskLocal}
import monix.execution.Scheduler.Implicits.global
import monix.execution.misc.Local
import monix.execution.schedulers.CanBlock

import scala.concurrent.duration.Duration
import scala.util.Random

object MonixMain extends App {
  val api = new Api(new Dao)

  val apiCall =
    for {
      _ <- MDCAdapter.local.bind(Map("user" -> "1")) {
        api.getRecipeForPublisher(34)
      }
      _ = Logger.log("Without context")
    } yield ()

  apiCall.runSyncUnsafeOpt(Duration.Inf)
}

object Monix {
  implicit val opts: Task.Options = Task.defaultOptions.enableLocalContextPropagation

  object MDCAdapter {

    val local: TaskLocal[Map[String, String]] =
      TaskLocal[Map[String, String]](Map.empty)
        .runSyncUnsafeOpt()

    val localUnsafe: Local[Map[String, String]] =
      local.local.runSyncUnsafeOpt()
  }

  object Logger {
    def log(msg: String): Unit = {
      val mdc = MDCAdapter.localUnsafe()
      val thread = Thread.currentThread().getName
      println(s"$msg\nthread: $thread\nmdc: $mdc")
      println("=" * 50)
    }
  }

  class Api(dao: Dao) {
    def getRecipeForPublisher(id: Int): Task[Recipe] = {
      for {
        publisher <- dao.getPublisher(id)
        recipeId <- Task.delay(publisher.recipes.head)
        recipe <- dao.getRecipe(recipeId)
        _ = Logger.log(s"The recipe: $recipe")
      } yield recipe
    }
  }

  class Dao {
    def getPublisher(id: Int): Task[Publisher] = {
      Task.evalAsync {
        Logger.log(s"Asked for publisher: $id")
        Publisher(id, Seq(Random.nextInt(100)))
      }
    }

    def getRecipe(id: Int): Task[Recipe] = {
      Task.evalAsync {
        Logger.log(s"Asked for recipe: $id")
        Recipe(id)
      }
    }
  }
}
