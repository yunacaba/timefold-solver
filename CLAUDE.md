# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

Timefold Solver uses Maven as its build system. Choose the appropriate command based on your needs:

- **Fast build**: `mvn clean install -Dquickly` - Skips checks and analysis (~1 min)
- **Normal build**: `mvn clean install` - Runs tests and checks code style (~17 min)
- **Full build**: `mvn clean install -Dfull` - Runs all checks and creates documentation (~20 min)
- **Documentation only**: Run `mvn clean install` in the `docs/` directory

## Testing

- Run all tests: `mvn test`
- Run specific test: `mvn test -Dtest=ClassName`
- Run tests for specific module: `mvn test -pl core` (or other module names)

## Code Architecture

### Module Structure

This is a multi-module Maven project with the following key modules:

- **core**: The main planning engine implementation (`ai.timefold.solver.core`)
- **test**: Test utilities and fixtures
- **persistence**: Data persistence integrations
- **benchmark**: Performance benchmarking tools
- **spring-integration**: Spring Framework integration
- **quarkus-integration**: Quarkus Framework integration
- **tools**: Additional tooling including web UI
- **migration**: Migration utilities
- **build**: Build configuration and IDE setup

### Core Package Structure

The core module follows a layered architecture:

- **api**: Public API packages (backwards compatible)
  - `domain`: Planning entity and solution definitions
  - `function`: Constraint functions and score calculation
  - `score`: Score types and calculations
  - `solver`: Main solver API

- **config**: Configuration classes (backwards compatible)

- **impl**: Internal implementation (not for external use)
  - `bavet`: Constraint streaming engine
  - `constructionheuristic`: Construction algorithms
  - `localsearch`: Local search algorithms
  - `exhaustivesearch`: Brute force algorithms
  - `partitionedsearch`: Parallel solving
  - `domain`: Domain model implementation
  - `score`: Score calculation implementation
  - `solver`: Solver implementation
  - `move`: Move operations for algorithms

### Backwards Compatibility

- **Public API** (`api` packages): 100% backwards compatible
- **Configuration** (`config` packages): 100% backwards compatible
- **Implementation** (`impl` packages): No compatibility guarantees - internal use only

## Development Guidelines

### Code Style
- Code is automatically formatted during Maven builds
- Always run `mvn clean install` before creating PRs
- Follow existing patterns and conventions in the codebase

### Commit Messages
Use Conventional Commits format with these prefixes:
- `feat`: New features
- `fix`: Bug fixes
- `docs`: Documentation changes
- `perf`: Performance improvements
- `test`: Test changes
- `build`: Build system changes
- `ci`: CI configuration changes
- `revert`: Reverting changes
- `deps`: Dependency updates
- `chore`: Other changes

### Fail Fast Philosophy
- Validate inputs at method entry points
- Provide clear error messages with context and suggested fixes
- Check invalid states as early as possible (compile-time > startup-time > runtime)

### Exception Messages
Include variable names, values, and context in exception messages:
```java
if (fooSize < 0) {
    throw new IllegalArgumentException("The fooSize (" + fooSize + ") of bar (" + this + ") must be positive.");
}
```

## Key Features

Timefold Solver is an AI constraint solver for Java and Kotlin that optimizes planning problems like:
- Vehicle Routing Problem
- Employee Rostering
- Maintenance Scheduling
- Task Assignment
- School Timetabling
- Cloud Optimization

The solver uses various algorithms including construction heuristics, local search, and exhaustive search to find optimal solutions.