# ParticleRender

ParticleRender is a Java particle rendering library for Minecraft servers, with a YAML emitter graph and a standalone browser editor. The editor builds to static files and needs no application server or API at runtime.

## Repository layout

| Directory | Purpose |
| --- | --- |
| `render/` | Particle types, packet data, sources, and protocol encoding. |
| `render-bukkit/` | Bukkit/Paper plugin and the `ParticleRender.render(...)` API for sending effects to players. |
| `flow/` | Emitter graph, YAML decoders, conditions, and geometry emitters. |
| `web/` | Static graph editor and Three.js particle preview. |
| `mappings/` | Protocol mappings packaged with the renderer. |
| `dumper/` | Mapping utility; currently excluded from the parent Maven build. |

## Build

The Java modules use Maven and Java source level 16. Build the modules listed in the parent POM from the repository root:

```bash
mvn package
```

The web editor is an independent npm project:

```bash
cd web
npm install
npm run build
```

Serve `web/dist/` with any static HTTP server, such as nginx. For local development, run `npm run dev` from `web/`. Run `npm test` there to check graph import/export, conditions, geometry, packet behavior, and the particle preview.

## Emitter graph format

[`flow/src/main/resources/ref.yml`](flow/src/main/resources/ref.yml) is the complete example and the reference fixture for the editor. The graph is a **top-level map of node IDs**. `__root__` is required and is the starting emitter. Connections use destination node IDs as keys. A `base:particle` node emits particles and has no outgoing connections.

```yaml
__root__:
  type: base:root
  connections:
    circle: '%3'

circle:
  type: base:circle
  points: 32
  radius: 2
  offsets: '0;1;0'
  rotation: '90;0;0'
  dist: outward
  connections:
    flame: ''

flame:
  type: base:particle
  particle: flame
  count: 0
  maxSpeed: 0
```

The built-in types are `base:root`, `base:particle`, `base:circle`, `base:sphere`, `base:line`, `base:rectangle`, and `base:box_surface`. Geometry nodes can fan out into multiple contexts before downstream nodes run. Vector values use `X;Y;Z`; geometry rotations are in degrees. The particle node supports `count`, `maxSpeed`, optional `offsets` and `dist`, `overrideLimiter`, `alwaysShow`, and particle-specific `data`.

Connection conditions are evaluated before calling the target node. An empty string runs on every tick. The Java parser accepts comparisons (`<N`, `<=N`, `>N`, `>=N`), a positive period (`%N`), and `&` to combine them. For example, `'<100 & %5'` runs every five ticks while the tick is below 100. Ticks start at 0.

The web editor adds a `$web` JSON string to each exported node for its display name and canvas position. The Java emitter decoders ignore that field. Node IDs and connection keys remain unchanged.

## Web editor

Open `web/dist/` through a static server. The editor initially loads a bundled copy of `ref.yml`. Use **Import YAML** or drag a YAML file onto the page to load another graph. **Copy YAML** and **Download YAML** export the Java-compatible top-level map.

- Right-click the graph to create a node. Drag an output port to an input port to connect them; drag an edge grip to reconnect or detach. Select a node to edit its properties.
- Select a connection to type its condition, or use the `%N`, `<N`, and `>N` helpers below the input.
- Select a geometry node to see its samples and transform gizmo in the 3D viewport. Move and Rotate update the node's `offsets` and `rotation` fields.
- Use **Play**, **Pause**, **Tick**, **Restart**, and **Clear particles** to control the preview. Clear removes visible particles without changing Play/Pause. The overlay estimates packets and traffic per tick and per second.
- Orbit mode uses mouse drag, right drag to pan, and the wheel to zoom. In free camera mode, hold the right mouse button to look and move with physical WASD keys (also works on a Russian layout). Space or E moves up; Shift or Q moves down. The wheel moves the camera and adjusts movement speed while right mouse is held. The cursor is never locked.

The browser evaluator follows the Java graph's connection and context flow. Terminal particle nodes feed the vanilla-oriented preview code transferred from `ParticleWebEditor`, including its particle-specific simulation and sprites. The preview is an estimate of client appearance and network traffic, not a live Minecraft client or packet capture. See [`web/README.md`](web/README.md) for editor implementation notes.
