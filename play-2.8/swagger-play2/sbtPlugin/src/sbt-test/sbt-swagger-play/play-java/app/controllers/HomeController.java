package controllers;

import play.mvc.*;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;

public class HomeController extends Controller {

    @Operation(description = "Index Page")
    @ApiResponse(content = @Content(schema = @Schema(implementation = String.class)))
    public Result index() {
        return ok("Hello");
    }
}
