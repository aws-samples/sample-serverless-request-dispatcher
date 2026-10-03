# Sample Product Service

A reference implementation of a microservice built with the [Serverless Request Dispatcher](../serverless-request-dispatcher/) library. Use this as a starting point when building your own lightweight Java microservices on AWS Lambda.

## Setup

The library dependency resolves from your local Maven repository or a published remote repository. See the [root README — Getting Started](../README.md#getting-started) for all options.

For local development, build the library from source first (one-time):

```bash
cd ../serverless-request-dispatcher
mvn clean install
```

Then build this sample:

```bash
mvn clean package
```

## Run Locally

The Lambda function requires the following environment variables (set automatically when deployed via SAM, but must be provided for local testing):

- `PRODUCT_TABLE` — DynamoDB table name
- `PRODUCT_IMAGES_BUCKET` — S3 bucket name

For fully local development, you can use [DynamoDB Local](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.html) and [LocalStack](https://localstack.cloud/) for S3, but this requires modifying `DynamoDbConfig` and `S3Config` to add a local endpoint override. Alternatively, deploy the stack first and test against real AWS resources.

To run with SAM (uses a Docker container with the env vars from `template.yaml`):

```bash
sam local start-api
```

This requires Docker. The Lambda runs locally in a container but connects to real AWS services (DynamoDB, S3) using your AWS credentials — there is no local endpoint override. Deploy the stack first with `sam deploy --guided` to create the resources, then use `sam local start-api` to invoke the function locally.

## Test

```bash
curl http://localhost:3000/api/products
curl http://localhost:3000/api/products/1
curl "http://localhost:3000/api/products?category=Electronics"
curl -X POST http://localhost:3000/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Keyboard","category":"Electronics","price":79.99}'
curl -X PUT http://localhost:3000/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"Gaming Laptop","category":"Electronics","price":1299.99}'
curl -X DELETE http://localhost:3000/api/products/1
```

Or: `./scripts/test-api.sh`

## Deploy

```bash
sam deploy --guided
```

## Cleanup

To avoid ongoing charges, delete the stack:

```bash
sam delete --stack-name <your-stack-name>
```

This removes all resources created by the template including the KMS key, DynamoDB table, S3 buckets, and Lambda function. The buckets have versioning and Object Lock enabled, so delete all object versions from them first if they contain any; CloudFormation can't delete a bucket that isn't empty.

## Structure

```
├── pom.xml
├── template.yaml
├── scripts/test-api.sh
└── src/main/java/com/amazonaws/serverless/sample/
    ├── LambdaHandler.java
    ├── controller/ProductController.java
    ├── service/
    │   ├── ProductService.java
    │   └── S3StorageService.java
    ├── repository/ProductRepository.java
    ├── model/Product.java
    ├── config/
    │   ├── AppComponent.java
    │   ├── AppModule.java
    │   ├── AppRequestDispatcher.java
    │   ├── DynamoDbConfig.java
    │   ├── LambdaCredentials.java
    │   └── S3Config.java
    └── benchmark/            # Comparison handlers used only by ../benchmarks
        ├── MinimalHandler.java
        └── PlainHandler.java
```

## Creating a New Microservice From This Template

1. Copy this directory
2. Rename the package, artifactId, and groupId
3. Replace `ProductController` with your own controllers
4. Update `AppModule` to provide your controllers
5. Update `template.yaml` with your routes
6. Delete the `benchmark` package if you don't need the cold start comparison

See the [root README](../README.md) for the full walkthrough.
