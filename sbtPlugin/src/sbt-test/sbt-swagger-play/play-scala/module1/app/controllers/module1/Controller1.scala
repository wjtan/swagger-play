package controllers.module1

import javax.inject._
import play.api._
import play.api.mvc._

import io.swagger.v3.oas.annotations._
import io.swagger.v3.oas.annotations.media._
import io.swagger.v3.oas.annotations.responses._

@Singleton
class Controller1 @Inject()(val controllerComponents: ControllerComponents) extends BaseController {

  @Operation
  @ApiResponse(content = Array(new Content(schema = new Schema(implementation = classOf[String]))))
  def add1() = Action { implicit request: Request[AnyContent] =>
    Ok("Add 1")
  }
}
