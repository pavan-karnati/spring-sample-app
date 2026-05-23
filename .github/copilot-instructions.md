# Copilot Instructions

## Build & Run

All commands must be run inside the devbox environment. Use `devbox shell` to enter, or prefix with `devbox run --`.

```bash
# Run the app
./mvnw spring-boot:run

# Build
./mvnw clean package

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=HelloControllerTest

# Run a single test method
./mvnw test -Dtest=HelloControllerTest#counterIncrementsOnEachCall
```

Maven (`pom.xml`) is the primary build tool. Gradle (`build.gradle`) exists but its test dependencies are not aligned — always use Maven for building and testing.

## Architecture

Single-controller Spring Boot 4.x app serving an HTML page at `GET /`.

- `Application.java` — entry point; prints all Spring beans at startup (intentional diagnostic output)
- `HelloController.java` — the only controller; loads `quotes.json` once at startup via `@PostConstruct`, then serves a random quote with an in-memory visit counter on each request
- `src/main/resources/quotes.json` — 100 motivational quotes, structure: `{ "quotes": [{ "quote": "...", "author": "..." }] }`

The controller returns raw HTML strings (Java text blocks) directly from a `@RestController` — there is no template engine. HTML, CSS, and data are all produced inline in `HelloController.index()`.

The visit counter is an `AtomicInteger` — it is in-memory and resets on every app restart.

## Key Conventions

### Spring Boot 4.x breaking changes
This project uses Spring Boot **4.0.6** (Spring Framework 7.x). Several test APIs were removed:
- `@AutoConfigureMockMvc` does **not exist** — use `MockMvcBuilders.webAppContextSetup(context).build()` instead
- `@WebMvcTest` slices are removed — use `@SpringBootTest` for integration tests

### Integration test pattern
```java
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyIntegrationTest {
  @Autowired WebApplicationContext context;
  private MockMvc mockMvc;

  @BeforeAll
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }
}
```

### Unit test pattern
`HelloController` has no constructor injection — unit tests instantiate it directly and call `loadQuotes()` manually (it reads `quotes.json` from the classpath, which is available in tests too):
```java
HelloController controller = new HelloController();
controller.loadQuotes(); // must call before index()
```

### Jackson is not bundled
`spring-boot-starter-webmvc` does **not** include Jackson. It must be declared explicitly:
```xml
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
</dependency>
```

### Classpath resources
Load files from `src/main/resources/` using `ClassPathResource`, not `File` paths:
```java
new ClassPathResource("quotes.json").getInputStream()
```

## Dev Environment

Managed by [devbox](https://www.jetify.com/devbox). Packages: `jdk@17`, `maven`, `gradle`.

To enter the environment: `devbox shell`
