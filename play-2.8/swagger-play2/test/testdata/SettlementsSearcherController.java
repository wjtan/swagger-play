package testdata;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.servers.Server;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.Request;
import play.mvc.Result;

import java.util.List;

// @Api(value = "/apitest/search", tags = { "Search" })
@Server(url = "/api")
public class SettlementsSearcherController extends Controller {
    
    @Operation(
            summary = "Search for settlement",
            description = "Search for a settlement with personal number and property id.",
            method = "GET",
            operationId = "getsettlement",
            responses = {
              @ApiResponse(content = @Content(
                 array = @ArraySchema(schema = @Schema(implementation = Settlement.class)),
                 mediaType = "application/json"
               )
            )
          })
    @ApiResponses({
                    @ApiResponse(responseCode = "" + Http.Status.BAD_REQUEST, description = "Bad Request"),
                    @ApiResponse(responseCode = "" + Http.Status.UNAUTHORIZED, description = "Unauthorized"),
                    @ApiResponse(responseCode = "" + Http.Status.INTERNAL_SERVER_ERROR, description = "Server error")
    })
    @Parameters({
                         @Parameter(description = "Token for logged in user",
                                           name = "Authorization",
                                           required = false,
                                           schema = @Schema(type = "string"),
                                           in = ParameterIn.HEADER),
    })
    public Result search(Request req,
                         @Parameter(description = "A personal number of one of the sellers.", example = "0101201112345") String personalNumber,
                         @Parameter(description = "The cadastre or share id.", example = "1201-5-1-0-0", required = true) String propertyId) {
        return ok();
    }
    
    // @ApiOperation(value = "Search for settlement",
    // notes = "Search for a settlement with personal number and property id.",
    // httpMethod = "GET",
    // nickname = "getsettlementToo",
    // produces = "application/json",
    // response = int.class)
    // public Result searchToo(Request req, @ApiParam(value = "The cadastre or share id.", example = "1201-5-1-0-0", required =
    // true) String propertyId) {
    // return ok();
    // }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
class Settlement {
    
    @Schema(required = true)
    public String settlementId;
    
    @Schema(required = true)
    public List<String> sellers;
    
    @Schema
    public List<String> buyers;
    
    @Schema
    public Integer purchaseAmount;
    
}
