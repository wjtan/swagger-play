package testdata

import io.swagger.v3.oas.annotations._
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media._
import io.swagger.v3.oas.annotations.responses._
import io.swagger.v3.oas.annotations.servers.Server
import play.mvc.Result
import play.api.mvc.InjectedController

// @Api(value = "/apitest/document", description = "documents", tags = Array("Documents"))
@Server(url = "/api", description = "documents")
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
    new ApiResponse(responseCode = "400", description = "Bad Request"),
    new ApiResponse(responseCode = "401", description = "Unauthorized"),
    new ApiResponse(responseCode = "500", description = "Server error")))
  @Parameters(Array(
    new Parameter(description = "Token for logged in user.", name = "Authorization", required = false,
      schema = new Schema(`type`= "string"),
      in = ParameterIn.HEADER)))
  def accept(@Parameter(description = "Id of the settlement to accept a file on.") settlementId: String,
    @Parameter(description = "File id of the file to accept.") fileId: String): Result = {
    play.mvc.Results.ok
  }
}
