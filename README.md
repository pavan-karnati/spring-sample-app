# spring-sample-app

A Spring Boot 4 app that serves a random motivational quote with a visit counter on every page refresh.

## Tech Stack

- Java 17, Spring Boot 4.0.6
- Maven (primary build tool)
- Docker
- [Devbox](https://www.jetify.com/devbox) for local dev environment

## Getting Started

### Prerequisites

Install [devbox](https://www.jetify.com/devbox/docs/installing_devbox/) then run:

```bash
devbox shell
```

This gives you Java 17, Maven, and Gradle. No manual installation needed.

### Run the app

```bash
./mvnw spring-boot:run
```

Visit [http://localhost:8080](http://localhost:8080). Refresh to get a new quote.

### Run tests

```bash
# All tests
./mvnw test

# Single test class
./mvnw test -Dtest=HelloControllerTest

# Single test method
./mvnw test -Dtest=HelloControllerTest#counterIncrementsOnEachCall
```

## Docker

```bash
# Build the image (compiles the app inside Docker)
docker build -t spring-sample-app .

# Run
docker run -p 8080:8080 spring-sample-app

# With custom JVM options
docker run -p 8080:8080 -e JAVA_OPTS="-Xmx256m" spring-sample-app
```

## Pre-commit Hooks

Install [pre-commit](https://pre-commit.com/) then:

```bash
pip install pre-commit
pre-commit install                           # runs on git commit
pre-commit install --hook-type pre-push     # runs tests on git push
```

Hooks enforce trailing whitespace, secret detection, Java formatting (Google Java Style), Checkstyle, and compilation on every commit. Tests run on push.

To auto-fix formatting:

```bash
./mvnw spotless:apply
```

## Project Structure

```
src/
├── main/
│   ├── java/com/example/springboot/
│   │   ├── Application.java        # Entry point
│   │   └── HelloController.java    # Serves quotes + visit counter
│   └── resources/
│       └── quotes.json             # 100 motivational quotes
└── test/
    └── java/com/example/springboot/
        ├── HelloControllerTest.java            # Unit tests
        └── HelloControllerIntegrationTest.java # Integration tests (MockMvc)
```

## Adding Quotes

Edit `src/main/resources/quotes.json`:

```json
{
  "quotes": [
    { "quote": "Your quote here.", "author": "Author Name" }
  ]
}
```

Changes take effect on next app restart.
