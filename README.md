# Qubit's AAA Tweaks

Rendering optimizations and an isolated post-processing bloom pass for [AAA Particles](https://github.com/ChloePrime/AAAParticles)
on Minecraft 1.20.1. Ships as a single codebase for **both Fabric and Forge** (multiloader).

## Layout

| Project   | Contents                                                                                                                                         |
|-----------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| `common`  | Everything loader agnostic: the AAAParticles mixins, `ParticleBloomRenderer` (vanilla `PostChain`), the Cloth Config definition and all resources. |
| `fabric`  | `ClientModInitializer` entrypoint, client command bridge, Mod Menu config screen hook, `fabric.mod.json`.                                         |
| `forge`   | `@Mod` entrypoint, Forge client command event, `ConfigScreenHandler` hook, `META-INF/mods.toml`.                                                   |
| `buildSrc`| The `multiloader-common` / `multiloader-loader` convention plugins both loaders share.                                                             |

The `fabric` and `forge` projects compile the `common` sources directly (see `multiloader-loader.gradle`), so common code is
built once per loader against that loader's own AAAParticles and Cloth Config artifacts.

## Requirements

- **Java 21+** to run Gradle (Fabric Loom 1.15 requires it). The mod itself still targets Java 17 - `java_version` in
  `gradle.properties` controls the toolchain, and `org.gradle.java.home` points the daemon at a JDK 21.
- [AAA Particles](https://www.curseforge.com/minecraft/mc-mods/aaa-particles) 2.3.4+ and
  [Cloth Config](https://www.curseforge.com/minecraft/mc-mods/cloth-config) 11+ at runtime on both loaders.
- AAAParticles ships Architectury and ForgeConfigAPIPort as nested jars. Fabric Loader only unpacks those in
  production, so the `fabric` project declares them directly - without them the dev client dies in AAAParticles'
  entrypoint with `NoClassDefFoundError`.

## Building / running

```bash
./gradlew build              # both loader jars in fabric/build/libs and forge/build/libs
./gradlew :fabric:runClient  # Fabric dev client
./gradlew :forge:runClient   # Forge dev client
```

Run directories live under `run/` (`run/client`, `run/server`, `run/data` for Forge, `run/fabric-client` / `run/fabric-server`
for Fabric).

## In-game debugging

`/qubitsaaatweaks spawn` spawns the bundled `qubitsaaatweaks:test` Effekseer effect in front of the player.
