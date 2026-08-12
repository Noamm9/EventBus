# Simple Kotlin Gradle Template

A minimalist template for building Kotlin applications with Gradle.

## Tech Stack

- **Language**: Kotlin
- **Build System**: Gradle (Kotlin DSL)
- **Plugin**: ShadowJar (8.1.1)
- **Logging**: Logback Classic

## Project Structure

```text
├── build.gradle.kts        # Main build configuration
├── settings.gradle.kts     # Project settings
├── src/
│   └── main/
│       ├── kotlin/         # Your Kotlin source code
│       └── resources/      # Configuration files
```

## Getting Started

### Requirements

- [JDK 17+](https://adoptium.net/) installed on your machine.

### Building the Project

To compile the code and generate a "fat" JAR:

```bash
./gradlew build
```

The resulting JAR file will be located in `build/libs/`.

### Running the Application

You can run the generated JAR using the following command:

```bash
java -jar build/libs/SimpleKotlin.jar
```

## Customization

1. **Change Project Name**: Update `rootProject.name` in `settings.gradle.kts`.
2. **Update Dependencies**: Add or modify libraries in `build.gradle.kts`.
3. **Configure Logging**: Modify `src/main/resources/logback.xml` to adjust logging levels and appenders.
