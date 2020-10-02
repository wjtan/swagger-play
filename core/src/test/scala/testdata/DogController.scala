package testdata

import io.swagger.v3.oas.annotations._
import io.swagger.v3.oas.annotations.media._
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses._
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.servers.Server

import scala.concurrent.Future
import play.api.mvc.InjectedController

// todo - test for these
//@Api(value = "/apitest/dogs", description = "look after the dogs",
//  basePath = "xx",
//  position = 2,
//  produces = "application/json, application/xml",
//  consumes = "application/json, application/xml",
//  protocols = "http, https",
//  authorizations = Array(new Authorization(value = "oauth2",
//    scopes = Array(
//      new AuthorizationScope(scope = "vet", description = "vet access"),
//      new AuthorizationScope(scope = "owner", description = "owner access")
//    ))
//  )
//)
@Server(url = "/api", description = "look after the dogs")
object DogController extends InjectedController {

  @Operation(
    operationId = "addDog0",
    responses = Array(
      new ApiResponse(content = Array(
        new Content(schema = new Schema(implementation = classOf[String]))
      ))
    ))
  def add0(id: String) = Action {
    request => Ok("test case")
  }

  @Operation(
    operationId = "addDog1",
    method = "PUT",
    responses = Array(
      new ApiResponse(content = Array(
        new Content(schema = new Schema(implementation = classOf[String]))
      ))
    ))
  @RequestBody(description = "Dog object to add", required = true,
    content = Array(new Content(schema = new Schema(implementation = classOf[Dog]))))
  def add1 = Action {
    request => Ok("test case")
  }

  @Operation(
    operationId = "addDog2",
    description = "Adds a dogs better",
    method = "PUT",
    // nickname = "addDog2_nickname",
    security = Array(new SecurityRequirement(
      name = "oauth2",
      scopes = Array("vet", "owner")
    )),
    //protocols = "http"
  )
  @ApiResponses(Array())
  @RequestBody(description = "Dog object to add", required = true,
    content = Array(new Content(
      schema = new Schema(implementation = classOf[Dog]),
      mediaType = "application/json"
    ))
  )
  def add2 = Action {
    request => Ok("test case")
  }

  @Operation(
    description = "Add a new Dog",
    summary = "Adds a dogs nicely",
    method = "PUT",
    security = Array(new SecurityRequirement(
      name = "oauth2",
      scopes = Array("vet", "owner")),
      new SecurityRequirement(name = "api_key")
    ),
    //consumes = " application/json, text/yaml ",
    //protocols = "http, https"
  )
  @ApiResponses(Array(
    new ApiResponse(responseCode = "405", description = "Invalid input"),
    new ApiResponse(responseCode = "666", description = "Big Problem")))
  @RequestBody(description = "Dog object to add", required = true,
    content = Array(
      new Content(schema = new Schema(implementation = classOf[Dog]), mediaType = "application/json"),
      new Content(schema = new Schema(implementation = classOf[Dog]), mediaType = "text/yaml")
    ))
  def add3 = Action {
    request => Ok("test case")
  }

  @Operation(
    description = "Updates a new Dog",
    summary = "Updates dogs nicely",
    method = "POST")
  @ApiResponse(responseCode = "405", description = "Invalid input")
  @RequestBody(description = "Dog object to update", required = true,
    content = Array(new Content(schema = new Schema(implementation = classOf[Dog]))))
  def update = Action {
    request => Ok("test case")
  }

  @Operation(
    description = "Get Dog by Id",
    summary = "Returns a dog",
    responses = Array(
      new ApiResponse(content = Array(
        new Content(schema = new Schema(implementation = classOf[Dog]))
      ))
    ),
    method = "GET")
  @ApiResponses(Array(
    new ApiResponse(responseCode = "405", description = "Invalid input"),
    new ApiResponse(responseCode = "404", description = "Dog not found")))
  def get1(@Parameter(description = "ID of dog to fetch", required = true) id: Long) = Action {
    request => Ok("test case")
  }

  @Operation(
    description = "Get Dog by Id",
    summary = "Returns a dog",
    responses = Array(
      new ApiResponse(content = Array(
        new Content(schema = new Schema(implementation = classOf[Dog]), mediaType = "application/json")
      ))
    ),
    method = "GET")
  @ApiResponses(Array(
    new ApiResponse(responseCode = "405", description = "Invalid input"),
    new ApiResponse(responseCode = "404", description = "Dog not found")))
  def get2(@Parameter(description = "ID of dog to fetch", required = true) id: Long) = Action {
    request => Ok("test case")
  }

  @Operation(
    description = "Get Dog by Id",
    summary = "Returns a dog",
    responses = Array(
      new ApiResponse(content = Array(
        new Content(schema = new Schema(implementation = classOf[Dog]), mediaType = "application/json")
      ))
    ),
    method = "GET")
    //produces = "application/json, application/xml")
  @ApiResponses(Array(
    new ApiResponse(responseCode = "405", description = "Invalid input"),
    new ApiResponse(responseCode = "404", description = "Dog not found")))
  def get3(@Parameter(description = "ID of dog to fetch", required = true) id: Long) = Action {
    request => Ok("test case")
  }

  /*   Not Supported....routing is done elsewhere...
  - although could use this with a custom routing module
  -- one day..
  @GET
  @Path("/{petId}")
  */

  @Operation(
    description = "List Dogs",
    operationId = "listDogs",
    summary = "Returns all dogs",
    responses = Array(
      new ApiResponse(content = Array(
        new Content(array = new ArraySchema(schema = new Schema(implementation = classOf[Dog])))
      ))
    ),
    method = "GET")
  @Deprecated
  def list = Action {
    request => Ok("test case")
  }

  @Operation(description = "Method with numeric chars in name",
    summary = "get a Dog with id 33",
    method = "GET")
  @ApiResponses(Array(
    new ApiResponse(responseCode = "404", description = "Dog not found")))
  def get33 = Action {
    request => Ok("test case")
  }

  // use the Jax.ws annotations
  // Delete a Dog
  @Operation(operationId = "Delete", summary = "Deletes a user", method = "DELETE")
  def delete(
    @Parameter(name = "dogId", description = "dogId") userId: String) = Action.async {
    implicit request => Future.successful(Ok)
  }

  @Operation(operationId = "valueStr", summary = "notesStr", method = "GET")
  @Deprecated
  def deprecated = Action {
    request => Ok("test case")
  }

  def no_annotations = Action {
    request => Ok("test case")
  }

  def no_route = Action {
    request => Ok("test case")
  }

  @Operation(description = "unknown method name", method = "UNKNOWN")
  def unknown_method() = Action {
    request => Ok("test case")
  }

  @Operation(description = "undefined method name")
  def undefined_method() = Action {
    request => Ok("test case")
  }
}
