# Qubit's AAA Tweaks

Rendering optimizations and an isolated post-processing bloom pass for [AAA Particles](https://github.com/ChloePrime/AAAParticles)
on Minecraft 1.21.1. Ships as a single codebase for **both Fabric and NeoForge** (multiloader).

## Layout

| Project    | Contents                                                                                                                                        |
|------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `common`   | Everything loader agnostic: the AAAParticles mixins, `ParticleBloomRenderer` (vanilla `PostChain`), the Cloth Config definition and all resources. |
| `fabric`   | `ClientModInitializer` entrypoint, client command bridge, Mod Menu config screen hook, `fabric.mod.json`.                                         |
| `neoforge` | `@Mod` entrypoint, NeoForge client command event, `IConfigScreenFactory` hook, `META-INF/neoforge.mods.toml`.                                     |
| `buildSrc` | The `multiloader-common` / `multiloader-loader` convention plugins both loaders share.                                                           |

The `fabric` and `neoforge` projects compile the `common` sources directly (see `multiloader-loader.gradle`), so common code is
built once per loader against that loader's own AAAParticles and Cloth Config artifacts.

## Requirements

- **Java 25+** to run Gradle (Loom 1.19.x requires it) and **Java 21** for the mod itself (`java_version` in
  `gradle.properties` selects the compile toolchain, so the output stays 1.21.1-compatible).
  The daemon JDK is intentionally not committed: set `org.gradle.java.home=<JDK 25+ path>` in your own
  `~/.gradle/gradle.properties`, or run Gradle with `JAVA_HOME` pointed at a JDK 25+.
- [AAA Particles](https://www.curseforge.com/minecraft/mc-mods/aaa-particles) 2.3.4+ and
  [Cloth Config](https://www.curseforge.com/minecraft/mc-mods/cloth-config) 15+ at runtime on both loaders.
- AAAParticles ships Architectury and ForgeConfigAPIPort as nested jars. Fabric Loader only unpacks those in
  production, so the `fabric` project declares them directly - without them the dev client dies in AAAParticles'
  entrypoint with `NoClassDefFoundError`.

## Building / running

```bash
./gradlew build                 # both loader jars in fabric/build/libs and neoforge/build/libs
./gradlew :fabric:runClient     # Fabric dev client
./gradlew :neoforge:runClient   # NeoForge dev client
```

Run directories live under `run/` (`run/fabric-client` / `run/fabric-server` for Fabric, `run/neoforge-client` /
`run/neoforge-server` for NeoForge). The NeoForge dev client also ships Sodium + Iris, so the bloom pass
(which defaults to "Iris shaders only") can be exercised in dev.

## In-game debugging

`/qubitsaaatweaks spawn` spawns the bundled `qubitsaaatweaks:test` Effekseer effect in front of the player.
