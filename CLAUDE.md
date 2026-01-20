# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Clue is a command-line tool and web interface for inspecting Apache Lucene indexes. It provides interactive CLI mode, non-interactive command mode, and a Micronaut-based web UI for remote index inspection.

## Build Commands

```bash
./gradlew build              # Build, test, and produce JAR
./gradlew test               # Run JUnit 5 tests
./bin/clue.sh <index> [cmd]  # Run CLI (interactive if no cmd)
./bin/clue-web.sh            # Start web server (port 8080)
./bin/build_sample_index.sh <dir>  # Build sample index from cars.json
```

The build produces `build/libs/clue-${VERSION}.jar`. Requires Java 21+.

## Architecture

### Package Structure (`io.dashbase.clue`)
- `api/` - Plugin interfaces: `DirectoryProvider`, `QueryBuilder`, `AnalyzerFactory`, `IndexReaderFactory`
- `commands/` - 30+ CLI commands (read-only and mutating)
- `server/` - Micronaut web API (`ClueCommandResource`, `ClueWebContext`)
- `client/` - CLI readline support
- `util/` - Parsing utilities, doc ID matching

### Key Classes
- `ClueApplication` - Main entry point, handles CLI modes
- `LuceneContext` - Manages IndexReader/IndexWriter/IndexSearcher lifecycle
- `ClueCommand` - Abstract base for all commands, uses picocli annotations

### Plugin System (ServiceLoader)
Commands and directory providers are extensible via ServiceLoader:
- `META-INF/services/io.dashbase.clue.api.DirectoryProvider`
- `META-INF/services/io.dashbase.clue.commands.CommandPlugin`

Use `@Readonly` annotation for commands that work in read-only mode.

## Coding Conventions

- 4-space indentation
- Package: `io.dashbase.clue.*` (lowercase)
- Classes: `PascalCase`, methods/fields: `lowerCamelCase`, constants: `UPPER_SNAKE_CASE`
- New commands go in `src/main/java/io/dashbase/clue/commands/` and must be registered

## Testing

Tests use JUnit 5 (Jupiter). Test files:
- Unit tests: `*Test.java`
- Integration tests: `*IT.java`

`CommandTestSupport` provides utilities for building sample indexes and capturing command output.

## JShell Skills

The `skills/` directory contains JShell scripts for advanced Lucene index analysis:

```bash
./skills/clue-jshell.sh <index>   # Start jshell with Lucene classpath
jshell> help()                     # Show available skills
jshell> /open skills/<skill>.jsh   # Load a skill
```

Skills include: `field-info-load`, `top-terms`, `doc-values`, aggregations (sum, stats, cardinality, percentile, terms, histogram, range, nested).

For percentile skills, add t-digest: `jshell --class-path "build/libs/*:plugins/t-digest-3.3.jar"`

## Configuration

- `config/clue.yml` - CLI configuration (analyzer, directory builder, query builder)
- `config/clue-web.yml` - Web server configuration (includes `clue.web.dir` for index path)
