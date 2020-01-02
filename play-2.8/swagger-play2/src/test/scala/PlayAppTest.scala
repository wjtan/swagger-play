

import javax.inject.{Inject, Provider, Singleton}
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner
import org.junit.runner.RunWith
import play.api.{Configuration, Environment}
import play.api.http.HttpConfiguration
import play.api.inject.{Binding, Module}
import play.api.inject.guice.GuiceApplicationBuilder
import play.modules.swagger.SwaggerModule
import play.api.mvc._
import play.api.routing.Router.Routes
import play.api.routing._
import play.api.routing.sird._

class ScalaSimpleRouter @Inject() (val Action: DefaultActionBuilder) extends SimpleRouter {
  override def routes: Routes = {
    case GET(p"/") => Action {
      Results.Ok("ok")
    }
  }
}

@Singleton
class ScalaRoutesProvider @Inject() (playSimpleRouter: ScalaSimpleRouter, httpConfig: HttpConfiguration)  extends Provider[Router] {
  lazy val get = playSimpleRouter.withPrefix(httpConfig.context)
}


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
    .bindings(new SwaggerModule)
    .overrides(new Module {
      override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
        Seq(bind[Router].toProvider[ScalaRoutesProvider])
    })
    .build()
}
