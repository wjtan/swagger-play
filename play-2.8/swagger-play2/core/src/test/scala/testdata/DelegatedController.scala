package testdata

import io.swagger.v3.oas.annotations._
import play.api.mvc.{Action, ActionBuilder, AnyContent, ControllerHelpers}

class DelegatedController extends ControllerHelpers {

  import scala.concurrent.ExecutionContext.Implicits.global
  private val Action = new ActionBuilder.IgnoringBody()

  @Operation(operationId = "list")
  def list: Action[AnyContent] = Action { _ => Ok("test case")}
  @Operation(operationId= "list2")
  def list2: Action[AnyContent] = Action { _ => Ok("test case")}
  @Operation(operationId = "list3")
  def list3: Action[AnyContent] = Action { _ => Ok("test case")}
  @Operation(operationId = "list4")
  def list4: Action[AnyContent] = Action { _ => Ok("test case")}
  @Operation(operationId = "list5")
  def list5: Action[AnyContent] = Action { _ => Ok("test case")}
}
