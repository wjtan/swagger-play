package play.modules.swagger

import controllers.ApiHelpController
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import io.swagger.v3.oas.models.OpenAPI

class SwaggerModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    //bind[OpenAPI].to(new OpenAPI()),
    bind[RouteWrapper].toProvider[RouteProvider],
    bind[PlaySwaggerConfig].toProvider[PlaySwaggerConfigProvider],
    bind[PlayReaderProvider].toSelf,
    bind[PlayApiScanner].toSelf,
    bind[ApiListingCache].toSelf,

    bind[SwaggerPlugin].to[SwaggerPluginImpl].eagerly(),
    bind[ApiHelpController].toSelf
  )

}
