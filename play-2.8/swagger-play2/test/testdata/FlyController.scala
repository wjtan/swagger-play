package testdata

import io.swagger.v3.oas.annotations.Hidden
import play.api.mvc.InjectedController

@Hidden
class FlyController extends InjectedController {

  def list = Action {
    request =>
      Ok("test case")
  }

}

case class Fly(id: Long, name: String)
