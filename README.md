# Overview

The generated Java types for a protobuf message implement the `Message` interface. This provides handy methods like `.toBuilder()` for any generated type, so you can have a function that takes a `Message` parameter and be able to call `.toBuilder()` on it. So far so good!

However, there are also the `.newBuilder()` and `.parser()` static methods on the generated `Message` types. Since those are static methods, not instance methods, their presence can't be guaranteed by the `Message` interface, and thus there is no way to call those methods for any `Message` type (without reflection, that is). This library provides MethodHandle-based helpers to invoke those methods.

## Calling `newBuilder()`

`builderWrapper()` wraps the process of invoking `newBuilder()` and calling `build()`, invoking your provided builder-using logic in between. This allows you to create generic functions like this one to parse any `Message` from protobuf-style JSON:

```kotlin
private inline fun <reified T : Message> jsonParser(): (String) -> T {
    // normal protobuf json parser
    val jsonParser = JsonFormat.parser()

    // build a String -> T function that uses the json parser
    return builderWrapper { builder, json ->
        // do whatever you like with the builder instance
        jsonParser.merge(json, builder)
    }
}
```

You could use that to create a json parsing function for a particular type:

```kotlin
// reusable json -> T function
val favColorParser: (String) -> FavoriteColor = jsonParser<FavoriteColor>()
val favColor = favColorParser("""{"color": "blue", "priority": 17}""")
```

In this example we use Kotlin's `reified T`, but it can also be written to use a `Class<T>`, if that's more convenient for your use case.

## Calling `parser()`

`parser()` is simpler -- it just produces the `Parser` for the corresponding `Message` type.

```kotlin
// normal protobuf Parser, reuse as needed
val parser: Parser<FavoriteColor> = parser<FavoriteColor>()
val deserialized = parser.parseDelimitedFrom(someInputData)
```

# Usage

Artifacts are hosted in Maven Central, available as the `mavenCentral()` repository in Gradle.

In your `build.gradle.kts`, add:

```
implementation("com.classpass.oss.protobuf.reflection", "protobuf-reflection", "LATEST-VERSION-HERE")
```

# Contributing

We welcome contributions from everyone! See [CONTRIBUTING.md](CONTRIBUTING.md) for information on making a contribution.

## Formatting

The `check` task will check formatting (in addition to the other normal checks), and `formatKotlin` will auto-format.

## License headers

The `check` task will ensure that license headers are properly applied, and `licenseFormat` will apply headers for you.

# License

See [LICENSE](LICENSE) for the project license.
