# Serverless Request Dispatcher

> **Note:** This is a sample project intended for educational and demonstration purposes. It is not intended for production use without additional security hardening. Use at your own risk.

A lightweight routing library for building Java microservices on AWS Lambda. Spring Boot-style annotations with compile-time dependency injection via Dagger — fast cold starts, small footprint, no classpath scanning or proxy generation.

## The Problem

Spring Boot on Lambda means 5–10 second cold starts, 1 GB+ memory, and 50 MB+ deployment packages. Most of that overhead comes from runtime classpath scanning, proxy generation, and reflection-based dependency injection — none of which is needed in a serverless function.

## The Solution

This library gives you the same developer experience — `@GetMapping`, `@PostMapping`, `@PathVariable`, `@RequestParam` — but dependency injection is resolved at compile time using Dagger. No classpath scanning, no proxy generation, no embedded server.

| | This Library | Spring Boot on Lambda |
|---|---|---|
| Cold start | 500–800 ms | 5–10 s |
| Warm latency | 10–50 ms | 50–100 ms |
| Min memory | 256 MB | 1 GB+ |
| Package size | ~5 MB | 50 MB+ |

## Getting Started

Clone the repository and build the library:

```bash
git clone https://github.com/aws-samples/serverless-request-dispatcher.git
cd serverless-request-dispatcher

# Install the library to your local Maven repository
cd serverless-request-dispatcher
mvn clean install
```

Add the dependency to your microservice's `pom.xml`:

```xml
<dependency>
    <groupId>com.amazonaws.serverless</groupId>
    <artifactId>serverless-request-dispatcher</artifactId>
    <version>1.0.0</version>
</dependency>
```

Write a controller (paths are relative — the `basePath` in `AppRequestDispatcher` is prepended automatically):

```java
public class ProductController {

    @Inject
    public ProductController(ProductService productService, Gson gson) { ... }

    @GetMapping("/products")
    public String list(@RequestParam("category") String category) { ... }

    @GetMapping("/products/{id}")
    public String get(@PathVariable("id") String id) { ... }

    @PostMapping("/products")
    public String create(String body) { ... }

    @PutMapping("/products/{id}")
    public String update(@PathVariable("id") String id, String body) { ... }

    @DeleteMapping("/products/{id}")
    public String delete(@PathVariable("id") String id) { ... }
}
```

Wire it with Dagger:

```java
@Module
public class AppModule {
    @Provides @Singleton
    public List<Object> provideControllers(ProductController controller) {
        return List.of(controller);
    }
}

// The basePath "/api" is prepended to all annotation paths.
// @GetMapping("/products") becomes route "/api/products".
@Singleton
public class AppRequestDispatcher extends FrontControllerRequestDispatcher {
    @Inject
    public AppRequestDispatcher(List<Object> controllers) {
        super(controllers, "/api");
    }
}

// Dagger component — ties the module to the dependency graph.
// Dagger generates DaggerAppComponent at compile time.
@Component(modules = {AppModule.class})
@Singleton
public interface AppComponent {
    AppRequestDispatcher requestDispatcher();
}
```

Create a Lambda handler:

```java
public class LambdaHandler implements RequestHandler<AwsProxyRequest, AwsProxyResponse> {
    private final FrontControllerRequestDispatcher dispatcher;

    public LambdaHandler() {
        AppComponent component = DaggerAppComponent.create();
        this.dispatcher = component.requestDispatcher();
    }

    @Override
    public AwsProxyResponse handleRequest(AwsProxyRequest request, Context context) {
        try {
            Object result = dispatcher.invoke(request);
            return createResponse(200, result.toString());
        } catch (RouteException e) {
            // Route not found (no matching path/method registered)
            return createResponse(404, "{\"error\":\"" + e.getMessage() + "\"}");
        } catch (InvocationTargetException e) {
            // Exception thrown inside a controller method
            if (e.getCause() instanceof RouteException) {
                return createResponse(400, "{\"error\":\"" + e.getCause().getMessage() + "\"}");
            }
            return createResponse(500, "{\"error\":\"Internal server error\"}");
        } catch (Exception e) {
            return createResponse(500, "{\"error\":\"Internal server error\"}");
        }
    }

    private AwsProxyResponse createResponse(int statusCode, String body) {
        Headers headers = new Headers();
        headers.putSingle("Content-Type", "application/json");
        return new AwsProxyResponse(statusCode, headers, body);
    }
}
```

Deploy with SAM (from the `sample-product-service/` directory):

```bash
cd sample-product-service
mvn clean package
sam deploy --guided
```

## Annotations

| Annotation | Purpose |
|---|---|
| `@GetMapping("/path")` | Handle GET requests |
| `@PostMapping("/path")` | Handle POST requests |
| `@PutMapping("/path")` | Handle PUT requests |
| `@PatchMapping("/path")` | Handle PATCH requests |
| `@DeleteMapping("/path")` | Handle DELETE requests |
| `@PathVariable("name")` | Extract value from URL path |
| `@RequestParam("name")` | Extract value from query string (nullable — `null` if absent) |

Unannotated `String` parameters receive the request body. `AwsProxyRequest` and `Headers` types are injected automatically.

## Architecture

![Architecture Diagram](architecture.drawio.png)

## How It Works

At compile time, Dagger generates plain Java code that wires your dependencies with direct constructor calls — no reflection for DI. At Lambda cold start, the `FrontControllerRequestDispatcher` scans your controllers once and builds an O(1) HashMap of routes. Each incoming request is a single map lookup to find the right method.

**Route matching:** Routes are matched against `AwsProxyRequest.getResource()`, which returns the API Gateway resource template (e.g., `/api/products/{id}`) — not the resolved path. Path variable values are extracted from `getPathParameters()`. This is how API Gateway proxy integration works: the template is fixed, and actual path segments are passed as parameters.

```
Compile time                              Runtime (cold start)
┌─────────────────────────┐              ┌─────────────────────────┐
│ Dagger reads @Module,   │              │ DaggerAppComponent      │
│ @Inject, @Component     │──generates──▶│ .create()               │
│ Validates dependency    │              │                         │
│ graph, generates Java   │              │ Plain new() calls for DI│
└─────────────────────────┘              │ No classpath scanning   │
                                         └─────────────────────────┘
```

## Sample Application

The [sample-product-service](sample-product-service/) is a complete working microservice with DynamoDB and S3 integration. Use it as a starting point for your own services.

```bash
# Build the library (one-time)
cd serverless-request-dispatcher
mvn clean install

# Run the sample
cd ../sample-product-service
mvn clean package
sam local start-api
```

> `sam local start-api` requires Docker. The Lambda runs locally in a container but connects to real AWS services (DynamoDB, S3) using your AWS credentials. Deploy the stack first with `sam deploy --guided` to create the resources, then use `sam local start-api` to invoke the function locally. See [sample-product-service/README.md](sample-product-service/README.md#run-locally) for details.

```bash
curl http://localhost:3000/api/products
curl -X POST http://localhost:3000/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Keyboard","category":"Electronics","price":79.99}'
```

## Cost and Cleanup

Deploying this sample creates AWS resources (Lambda, API Gateway, DynamoDB, S3, KMS, SQS) that may incur charges. Usage within the [AWS Free Tier](https://aws.amazon.com/free/) is typically sufficient for experimentation, but costs will vary based on request volume and data stored.

To tear down all resources when you're done:

```bash
sam delete --stack-name <your-stack-name>
```

This removes the CloudFormation stack and all associated resources. Verify deletion in the AWS Console to ensure no resources remain.

## Requirements

- Java 21+
- Maven 3.6+
- AWS SAM CLI

## License

MIT-0 — See [LICENSE](LICENSE)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md)