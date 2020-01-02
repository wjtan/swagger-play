package testdata

import io.swagger.v3.oas.annotations._
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media._
import io.swagger.v3.oas.annotations.responses._
import io.swagger.v3.oas.annotations.servers.Server
import play.mvc.{Http, Result}
import play.api.mvc.InjectedController

// @Api(value = "/apitest/pointsofinterest", description = "Points of interest")
@Server(url = "/api")
class PointOfInterestController extends InjectedController {
  @Operation(
    summary = "Get points of interest",
    description = "Returns points of interest",
    method = "GET",
    operationId = "pointsofinterest",
    responses = Array(
      new ApiResponse(content = Array(
        new Content(mediaType = "application/json")
      ))
    )
  )
  @ApiResponses(Array(
    new ApiResponse(responseCode = "400", description = "Bad Request"),
    new ApiResponse(responseCode = "401", description = "Unauthorized"),
    new ApiResponse(responseCode = "500", description = "Server error")))
  @Parameter(description = "Token for logged in user.", name = "Authorization", required = false, schema = new Schema(`type` = "string"), in = ParameterIn.HEADER)
  def list(@Parameter(description = "Minimum easting for provided extent", required = true, schema = new Schema(defaultValue = "-19448.67")) eastingMin: Double,
    @Parameter(description = "Minimum northing for provided extent", required = true, schema = new Schema(defaultValue = "2779504.82")) northingMin: Double,
    @Parameter(description = "Maximum easting for provided extent", required = true, schema = new Schema(defaultValue = "-17557.26")) eastingMax: Double,
    @Parameter(description = "Maximum northing for provided extent", required = true, schema = new Schema(defaultValue = "2782860.09")) northingMax: Double): Result = {
    play.mvc.Results.ok
  }
}