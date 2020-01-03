package testdata

import io.swagger.v3.oas.annotations._
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media._
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses._
import io.swagger.v3.oas.annotations.servers.Server
import play.api.mvc.InjectedController

@Server(url = "/api", description = "play with cats")
class CatController extends InjectedController {

  @Operation(
    // operationId = "addCat1",
    method = "PUT",
    responses = Array(
      new ApiResponse(content = Array(
        new Content(schema = new Schema(implementation = classOf[String]))
      ))
    )
  )
  @RequestBody(description = "Cat object to add", required = true,
    content = Array(
      // new Content(schema = new Schema(`type` = "testdata.Cat"))
      new Content(schema = new Schema(implementation = classOf[Cat]))
    ))
  def add1 = Action {
    request => Ok("test case")
  }

  @Operation(
    description = "Updates a new Cat",
    summary = "Updates cats nicely",
    method = "POST")
  @ApiResponse(responseCode = "405", description = "Invalid input")
  @RequestBody(description = "Cat object to update", required = true,
    content = Array(
      //new Content(schema = new Schema(`type` = "testdata.Cat"))
      new Content(schema = new Schema(implementation = classOf[Cat]))
    ))
  def update = Action {
    request => Ok("test case")
  }

  @Operation(
    description = "Get Cat by Id",
    summary = "Returns a cat",
    responses = Array(
      new ApiResponse(content = Array(
        new Content(schema = new Schema(implementation = classOf[Cat]))
      ))
    ),
    method = "GET")
  @ApiResponses(Array(
    new ApiResponse(responseCode = "405", description = "Invalid input"),
    new ApiResponse(responseCode = "404", description = "Cat not found")))
  def get1(@Parameter(name = "ID of cat to fetch", required = true) id: Long) = Action {
    request => Ok("test case")
  }

  @Operation(
    description = "List Cats",
    operationId = "listCats",
    summary = "Returns all cats",
    responses = Array(
      new ApiResponse(content = Array(
        new Content(array =
          new ArraySchema(schema = new Schema(implementation = classOf[Cat]))
        )
      ))
    ),
    method = "GET")
  @Deprecated
  def list = Action {
    request => Ok("test case")
  }

  def no_route = Action {
    request => Ok("test case")
  }

  @Operation(
    description = "test issue #43",
    operationId = "test issue #43_nick",
    summary = "test issue #43_notes",
    responses = Array(
      new ApiResponse(content = Array(
        new Content(array =
          new ArraySchema(schema = new Schema(implementation = classOf[Cat]))
        )
      ))
    ),
    method = "GET")
  @Parameters(Array(
    new Parameter(
      name = "test_issue_43_implicit_param",
      schema = new Schema(implementation = classOf[Int]),
      description = "test issue #43 implicit param",
      in = ParameterIn.QUERY)))
  def testIssue43(test_issue_43_param: Option[Int]) = Action {
    request => Ok("test issue #43")
  }
}

case class Cat(id: Long, name: String)
