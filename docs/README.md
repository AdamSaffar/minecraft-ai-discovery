# Project documentation

This directory contains public documentation for implemented and verified parts of the Minecraft AI Discovery project.

The project is currently in its initial proof-of-concept stage. Additional documentation will be added as individual components become meaningful, testable implementations.

## Experiments

- [Semantic observation proof of concept](experiments/semantic-observation-poc.md) — verified renderer-derived class output for basic opaque blocks in Minecraft Java Edition 1.16.1.
- [Solid-block semantic classes](experiments/solid-block-semantic-classes.md) — verified manifest-backed unsigned 16-bit class output for representative solid-layer blocks.
- [Cutout-block semantic classes](experiments/cutout-block-semantic-classes.md) — verified texture-shaped semantic coverage across solid, cutout-mipped, and cutout block-model layers.
- [Translucent observation diagnostic probe](experiments/translucent-observation-probe.md) — verified an opt-in researcher diagnostic for overlapping translucent contributions and GPU-applied base fog retention.
- [Lighting-retention diagnostic probe](experiments/lighting-retention-probe.md) — verified an opt-in, renderer-derived researcher measurement of lighting on aligned opaque/cutout base geometry.
