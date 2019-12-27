package testdata

import io.swagger.v3.oas.annotations._
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media._
import io.swagger.v3.oas.annotations.responses._

import play.mvc.{ Result, Http }
import play.api.mvc.InjectedController

// @Api(value = "/apitest/document", description = "documents", tags = Array("Documents"))
class DocumentController extends InjectedController {

  @Operation(
    description = "Register acceptance of a file on a settlement",
    summary = "Accept file",
    method = "POST",
    operationId = "acceptsettlementfile",
    responses = Array(
      new ApiResponse(content = Array(
        new Content(mediaType = "application/json")
      ))
    ))
  @ApiResponses(Array(
    new ApiResponse(responseCode = Http.Status.BAD_REQUEST.toString, description = "Bad Request"),
    new ApiResponse(responseCode = Http.Status.UNAUTHORIZED.toString, description = "Unauthorized"),
    new ApiResponse(responseCode = Http.Status.INTERNAL_SERVER_ERROR.toString, description = "Server error")))
  @Parameters(Array(
    new Parameter(description = "Token for logged in user.", name = "Authorization", required = false,
      schema = new Schema(`type`= "string"),
      in = ParameterIn.HEADER)))
  def accept(@Parameter(description = "Id of the settlement to accept a file on.") settlementId: String,
    @Parameter(description = "File id of the file to accept.") fileId: String): Result = {
    play.mvc.Results.ok
  }
}
