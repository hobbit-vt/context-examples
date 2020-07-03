package contexts

import contexts.VanillaFutures._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Random

object VanillaFuturesMain extends App {
  val api = new Api(new Dao)

  val apiCall =
    for {
      _ <- MDCAdapter.local.let(Map("user" -> "1")) {
        api.getRecipeForPublisher(34)
      }
      _ = MDCAdapter.local.remove()
      _ = Logger.log("Without context")
    } yield ()

  Await.result(apiCall, 1.minute)
}

object VanillaFutures {
  class DynamicContext(val map: Map[DynamicContext.Key, Any]) {
    def put(key: DynamicContext.Key, value: Any): DynamicContext =
      new DynamicContext(map + (key -> value))
    def remove(key: DynamicContext.Key): DynamicContext =
      new DynamicContext(map - key)
    def get[A](key: DynamicContext.Key): A =
      map.get(key).orNull.asInstanceOf[A]
  }

  object DynamicContext {
    class Key

    private[this] val localCtx = new ThreadLocal[DynamicContext] {
      override def initialValue(): DynamicContext = new DynamicContext(Map.empty)
    }
    def current(): DynamicContext = localCtx.get()
    def restore(context: DynamicContext): Unit = localCtx.set(context)

    def put(key: Key, value: Any): Unit =
      localCtx.set(localCtx.get.put(key, value))
    def get[A](key: Key): A =
      localCtx.get.get(key)
    def remove(key: Key): Unit =
      localCtx.set(localCtx.get.remove(key))
  }
  class Local[A] {
    private val key = new DynamicContext.Key
    def set(value: A): Unit = DynamicContext.put(key, value)
    def get(): Option[A] = Option(DynamicContext.get(key))
    def remove(): Unit = DynamicContext.remove(key)
    def update(value: Option[A]): Unit =
      value match {
        case Some(value) => set(value)
        case None        => remove()
      }
    def let[B](newValue: A)(fn: => Future[B]): Future[B] = {
      val oldValue = get()
      try {
        set(newValue)
        val resultF = fn
        resultF.onComplete(_ => update(oldValue))(SameThreadEC)
        resultF
      } finally {
        update(oldValue)
      }
    }
  }

  object SameThreadEC extends ExecutionContext {
    override def execute(runnable: Runnable): Unit = runnable.run()
    override def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
  }
  class PropagatingEC(underlying: ExecutionContext) extends ExecutionContext {
    override def execute(runnable: Runnable): Unit = {
      val prevContext = DynamicContext.current()
      underlying.execute(() => {
        val currentContext = DynamicContext.current()
        try {
          DynamicContext.restore(prevContext)
          runnable.run()
        } finally {
          DynamicContext.restore(currentContext)
        }
      })
    }
    override def reportFailure(cause: Throwable): Unit = underlying.reportFailure(cause)
  }
  implicit val globalPropagationContext: ExecutionContext =
    new PropagatingEC(scala.concurrent.ExecutionContext.global)



  object MDCAdapter {
    val local = new Local[Map[String, String]]
  }

  object Logger {
    def log(msg: String): Unit = {
      val mdc = MDCAdapter.local.get().getOrElse(Map.empty)
      val thread = Thread.currentThread().getName
      println(s"$msg\nthread: $thread\nmdc: $mdc")
      println("=" * 50)
    }
  }

  class Api(dao: Dao) {
    def getRecipeForPublisher(id: Int): Future[Recipe] = {
      for {
        publisher <- dao.getPublisher(id)
        recipeId <- Future.successful(publisher.recipes.head)
        recipe <- dao.getRecipe(recipeId)
        _ = Logger.log(s"The recipe: $recipe")
      } yield recipe
    }
  }

  class Dao {
    def getPublisher(id: Int): Future[Publisher] = {
      Future {
        Logger.log(s"Asked for publisher: $id")
        Publisher(id, Seq(Random.nextInt(100)))
      }
    }

    def getRecipe(id: Int): Future[Recipe] = {
      Future {
        Logger.log(s"Asked for recipe: $id")
        Recipe(id)
      }
    }
  }
}
