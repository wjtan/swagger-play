package play.modules.swagger

import controllers.ApiHelpController
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import play.modules.swagger.util.SwaggerContext

class SwaggerModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[SwaggerContext].toSelf,
    bind[RouteWrapper].toProvider[RouteProvider],
    bind[PlaySwaggerConfig].toProvider[PlaySwaggerConfigProvider],
    bind[PlayReader].toSelf,
    bind[PlayApiScanner].toSelf,
    bind[ApiListingCache].toSelf,

    bind[SwaggerPlugin].to[SwaggerPluginImpl].eagerly(),
    bind[ApiHelpController].toSelf
  )

}
