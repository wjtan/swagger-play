[![Build Status](https://travis-ci.org/wjtan/swagger-play.svg?branch=master)](https://travis-ci.org/wjtan/swagger-play)

# Swagger Play Integration

The goal of Swagger™ is to define a standard, language-agnostic interface to REST APIs which allows both humans and computers to discover and understand the capabilities of the service without access to source code, documentation, or through network traffic inspection. When properly defined via Swagger, a consumer can understand and interact with the remote service with a minimal amount of implementation logic. Similar to what interfaces have done for lower-level programming, Swagger removes the guesswork in calling the service.

Swagger-play is an integration specifically for the [Play framework](http://www.playframework.org).  It is written in scala but can be used with either java or scala-based play2 applications.

This is the fork of [Swagger Play](https://github.com/swagger-api/swagger-play) as their update is not frequent.

### Compatibility

Scala Versions | Play Version | Swagger Version | swagger-play version
---------------|--------------|-----------------|---------------------
2.12.8         | 2.7.x        | 2.0             | 1.7.0
2.12.3         | 2.6.x        | 2.0             | 1.6.1-SNAPSHOT
2.11.8         | 2.5.x        | 2.0             | 1.5.4-SNAPSHOT
2.11.6, 2.11.7 | 2.4.x        | 2.0             | 1.5.2
2.10.4, 2.11.1 | 2.3.x        | 1.2             | 1.3.12
2.9.1, 2.10.4  | 2.2.x        | 1.2             | 1.3.7
2.9.1, 2.10.4  | 2.1.x        | 1.2             | 1.3.5
2.8.1          | 1.2.x        | 1.2             | 0.1

### Usage

You can depend on pre-built libraries in maven central by adding the following dependency:

```
libraryDependencies ++= Seq(
  "io.swagger" %% "swagger-play2" % "1.7.0"
)
```

Or you can build from source.

```
cd modules/swagger-play2

sbt publishLocal
```

### Adding Swagger to your Play2 app

There are just a couple steps to integrate your Play2 app with swagger.

1\. Add the Swagger module to your `application.conf`
 
```
play.modules.enabled += "play.modules.swagger.SwaggerModule"
```
 
2\. Add the resource listing to your routes file (you can read more about the resource listing [here](https://github.com/swagger-api/swagger-core/wiki/Resource-Listing))

```

GET     /swagger.json           controllers.ApiHelpController.getResources

```

3\. Annotate your REST endpoints with Swagger annotations. This allows the Swagger framework to create the [api-declaration](https://github.com/swagger-api/swagger-core/wiki/API-Declaration) automatically!

In your controller for, say your "pet" resource:

```scala
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid ID supplied"),
    new ApiResponse(code = 404, message = "Pet not found")))
  def getPetById(
    @ApiParam(value = "ID of the pet to fetch") id: String) = Action {
    implicit request =>
      petData.getPetbyId(getLong(0, 100000, 0, id)) match {
        case Some(pet) => JsonResponse(pet)
        case _ => JsonResponse(new value.ApiResponse(404, "Pet not found"), 404)
      }
  }

```

What this does is the following:

* Tells swagger that the methods in this controller should be described under the `/api-docs/pet` path

* The Routes file tells swagger that this API listens to `/{id}`

* Describes the operation as a `GET` with the documentation `Find pet by Id` with more detailed notes `Returns a pet ....`

* Takes the param `id`, which is a datatype `string` and a `path` param

* Returns error codes 400 and 404, with the messages provided

In the routes file, you then wire this api as follows:

```
GET     /pet/:id                 controllers.PetApiController.getPetById(id)
```

This will "attach" the /api-docs/pet api to the swagger resource listing, and the method to the `getPetById` method above

Please note that the minimum configuration needed to have a route/controller be exposed in swagger declaration is to have an `Api` annotation at class level.

#### The ApiParam annotation

Swagger for play has two types of `ApiParam`s--they are `ApiParam` and `ApiImplicitParam`.  The distinction is that some
paramaters (variables) are passed to the method implicitly by the framework.  ALL body parameters need to be described
with `ApiImplicitParam` annotations.  If they are `queryParam`s or `pathParam`s, you can use `ApiParam` annotations.


# application.conf - config options
```
api.version (String) - version of API | default: "beta"
swagger.api.basepath (String) - base url | default: "http://localhost:9000"
swagger.filter (String) - classname of swagger filter | default: empty
swagger.api.info = {
  contact : (String) - Contact Information | default : empty,
  description : (String) - Description | default : empty,
  title : (String) - Title | default : empty,
  termsOfService : (String) - Terms Of Service | default : empty,
  license : (String) - Terms Of Service | default : empty,
  licenseUrl : (String) - Terms Of Service | default : empty
}
```

## Note on Dependency Injection
This plugin works by default if your application uses Runtime dependency injection.

Nevertheless, a helper is provided `SwaggerApplicationLoader` to ease the use of this plugin with Compile Time Dependency Injection. 

## Other Swagger-Play integrations
This Swagger-Play integration allows you to use [Swagger annotations](https://github.com/swagger-api/swagger-core/wiki/Annotations-1.5.X) on your Play actions to generate a Swagger spec at runtime. The libraries below take different approaches to creating an integration.

* [iheartradio/play-swagger](https://github.com/iheartradio/play-swagger) - Write a Swagger spec in your routes file
* [zalando/api-first-hand](https://github.com/zalando/api-first-hand) - Generate Play code from a Swagger spec

## License

Copyright 2011-2016 SmartBear Software

Copyright 2017-2020 Tan Wen Jun

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
[apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.