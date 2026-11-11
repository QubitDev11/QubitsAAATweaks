# Changelog

## 1.21.1-1.0.0
- Ported to Minecraft 1.21.1 on Java 21.
- NeoForge replaces Forge (Fabric + NeoForge multiloader), built on the 1.21.1 Multiloader template.
- Updated to AAA Particles 2.3.4 and Cloth Config 15 for 1.21.1.
- Ported the render code to the 1.20.5+ vertex/MeshData API (`Tesselator#begin`, `BufferBuilder#addVertex`,
  `BufferUploader`), `ResourceLocation.fromNamespaceAndPath`, and dropped the unused vanilla `ParticleRenderType`
  bloom hook (`ParticleRenderType#end` no longer exists in 1.21.1).

## 1.20.1-1.0.0
- Initial release of Qubit's AAA Tweaks.
- Added rendering optimizations and isolated post-processing bloom for AAAParticles.
