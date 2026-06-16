# Serverless Request Dispatcher — Library

The core routing library. This module produces the publishable JAR artifact.

See the [root README](../README.md) for usage documentation.

## Contents

```
src/main/java/com/amazonaws/serverless/requestdispatcher/annotation/
├── FrontControllerRequestDispatcher.java  # Core routing engine (abstract)
├── GetMapping.java                        # @GetMapping annotation
├── PostMapping.java                       # @PostMapping annotation
├── PutMapping.java                        # @PutMapping annotation
├── PatchMapping.java                      # @PatchMapping annotation
├── DeleteMapping.java                     # @DeleteMapping annotation
├── PathVariable.java                      # @PathVariable annotation
├── RequestParam.java                      # @RequestParam annotation
└── RouteException.java                    # Routing exception
```

## Build

```bash
# From this directory (serverless-request-dispatcher/)
mvn clean install
```

The JAR is installed to your local Maven repository (`~/.m2/repository`) and can be referenced by the sample application or any other project.
