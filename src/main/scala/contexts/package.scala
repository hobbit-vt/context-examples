import scala.language.higherKinds

package object contexts {
  case class Recipe(id: Int)
  case class Publisher(id: Int, recipes: Seq[Int])

  trait ApiInterface[F[_]] {
    def getRecipeForPublisher(id: Int): F[Recipe]
  }

  trait DaoInterface[F[_]] {
    def getPublisher(id: Int): F[Publisher]

    def getRecipe(id: Int): F[Recipe]
  }
}
