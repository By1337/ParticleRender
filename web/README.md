# Particle Graph Editor

```bash
npm install
npm run build
```

Serve `dist/` from any static server. `npm run dev` starts Vite for local editing; the built editor uses no API or backend. `npm test` checks the Java format fixture, connection semantics, packet spawning, and the transferred particle simulator.

Import a YAML file with **Import YAML** or by dropping it on the page. The default document is a static copy of `flow/src/main/resources/ref.yml`. Export produces the top-level emitter map required by `EmitterGraph.DECODER`. Node positions and display names are stored in the editor-only `$web` field, which the Java decoder ignores.

Drag an output port onto an input port to connect nodes. Drag an edge grip or a single incoming port to reconnect; release over empty space to detach. Right click in the graph to create a node at the cursor. The preview overlay distinguishes blue geometry samples from amber packet origins: a particle node can add its own offsets, and `count > 0` spreads client particles around each packet origin.

Each exported node includes a `$web` JSON string with its display name and graph position. The Java decoder ignores this field. The node ID remains the key used by `connections`; a new Particle node's display name follows its particle ID until renamed. The Particle picker filters IDs as you type.

**Free camera** switches from orbit to flight controls. Hold the right mouse button inside the 3D viewport to look around and move with physical WASD keys, including on a Russian keyboard layout. Space/E moves up; Shift/Q moves down. The wheel moves the camera and, while right mouse is held, adjusts movement speed. Releasing the button immediately stops camera look and movement. The cursor stays visible; Pointer Lock is not used. Select **Orbit camera** to return to orbit controls.

**Clear particles** removes visible particles without changing Play/Pause. The scene overlay estimates packet count and traffic per tick and per second. Select a connection to enter a condition directly or append `%N`, `<N`, or `>N` with the helper fields. Vector properties have separate X, Y, and Z inputs while YAML retains its `X;Y;Z` format.

The editor modules are separated into graph import/export, condition parsing, emitter definitions and geometry sampling, graph evaluation, Three.js preview, and UI. The preview uses `simulation.js`, `packet.js`, `registry.json`, and particle sprites transferred from `ParticleWebEditor`. It inherits that project's vanilla particle behavior and its documented visual approximations, including block/item placeholders and omitted client collisions.

Preview executes graph tick 0 first. `Restart` clears particles, tick, and seeded random state. A 10,000 particle cap keeps the browser responsive for large graphs. Cycle/excessive fan-out execution is stopped with an error.
