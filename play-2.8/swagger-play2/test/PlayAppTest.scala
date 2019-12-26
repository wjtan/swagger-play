
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner
import org.junit.runner.RunWith
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.modules.swagger.SwaggerModule
import play.api.test._
//import play.api.test.Helpers._
import play.api.mvc._
import play.api.routing._
import play.api.routing.sird._

@RunWith(classOf[JUnitRunner])
class PlayAppTest extends Specification {
  new GuiceApplicationBuilder()
    .configure("play.modules.disabled" ->
      Seq(
        "play.api.db.DBModule",
        "play.api.db.HikariCPModule",
        "play.db.DBModule",
        "play.db.ebean.EbeanModule",
        "play.api.db.evolutions.EvolutionsModule"
      ))
    .router(Router.from {
      case GET(p"/") => Action {
        Results.Ok("ok")
      }
    })
    .bindings(new SwaggerModule)
    .build()
}
