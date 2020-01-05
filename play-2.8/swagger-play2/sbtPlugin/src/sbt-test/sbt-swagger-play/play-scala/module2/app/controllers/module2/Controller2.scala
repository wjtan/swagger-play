package controllers.module2

import javax.inject._
import play.api._
import play.api.mvc._

import io.swagger.v3.oas.annotations._
import io.swagger.v3.oas.annotations.media._
import io.swagger.v3.oas.annotations.responses._

@Singleton
class Controller2 @Inject()(val controllerComponents: ControllerComponents) extends BaseController {

  @Operation
  @ApiResponse(content = Array(new Content(schema = new Schema(implementation = classOf[String]))))
  def add2() = Action { implicit request: Request[AnyContent] =>
    Ok("Add 2")
  }
}
