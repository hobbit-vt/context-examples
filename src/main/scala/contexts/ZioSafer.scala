package contexts

import contexts.ZioSafer._
import zio.blocking.Blocking
import zio.{FiberRef, UIO, ZIO}

import scala.util.Random

object ZioSaferMain extends App {
  def apiCall(api: Api, mdc: ZioMdc) =
    for {
      _ <- mdc.locally(Map("user" -> "1")) {
        api.getRecipeForPublisher(34)
      }
      _ = Logger.log("Without context")
    } yield ()

  val program = for {
    // DI
    mdcLocal <- FiberRef.make(Map.empty[String, String])
    mdc <- mdcLocal.unsafeAsThreadLocal
      .map(mdcThreadLocal => new ZioMdc(mdcLocal, mdcThreadLocal))
    api = new Api(new Dao, mdc)
    // Setting a logger
    _ = Logger.setMdc(mdc)
    // Program it self
    _ <- apiCall(api, mdc)
  } yield ()

  zio.Runtime.global.unsafeRun(program)
}

object ZioSafer {

  object Logger {
    trait Mdc {
      def data(): Map[String, String]
      def set(k: String, v: String)
      def locally[B](map: Map[String, String])(fn: UIO[B]): UIO[B]
    }
    object Mdc {
      def empty: Mdc = new Mdc {
        override def data(): Map[String, String] = Map.empty
        override def set(k: String, v: String): Unit = ()
        override def locally[B](map: Map[String, String])(fn: UIO[B]): UIO[B] = fn
      }
    }

    private var mdc: Mdc = Mdc.empty
    def setMdc(newMdc: Mdc): Unit = {
      mdc = newMdc
    }

    def log(msg: String): Unit = {
      val data = mdc.data()
      val thread = Thread.currentThread().getName
      println(s"$msg\nthread: $thread\nmdc: $data")
      println("=" * 50)
    }
  }

  class ZioMdc(
      mdcLocal: FiberRef[Map[String, String]],
      mdcUnsafe: ThreadLocal[Map[String, String]]
  ) extends Logger.Mdc {
    override def data(): Map[String, String] = {
      mdcUnsafe.get()
    }
    override def set(k: String, v: String): Unit =
      mdcUnsafe.set(mdcUnsafe.get() + (k -> v))

    override def locally[B](map: Map[String, String])(fn: UIO[B]): UIO[B] =
      mdcLocal.locally(map)(fn)
  }

  class Api(dao: Dao, mdc: Logger.Mdc) {
    def getRecipeForPublisher(id: Int): UIO[Recipe] = {
      for {
        publisher <- dao.getPublisher(id)
        recipeId <- ZIO.effectTotal(publisher.recipes.head)
        recipe <- dao.getRecipe(recipeId)
        _ = mdc.set("recipe", s"$recipeId")
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
