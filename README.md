# WorldToPng

A lightweight, embeddable library that renders a region of a Minecraft world into a top-down PNG
map image — off the main server thread — and lets you draw text, images and shapes on top.

It is **not a server plugin**. You add it to *your* plugin/mod as a Gradle/Maven dependency and
bundle it into your jar; nothing extra gets installed on the server. Your code then renders a map
and does whatever it likes with the PNG (save it, ship it to a website, hand it to a mod…).

- **Platform-neutral core, thin platform adapters.** The renderer, palette and drawing API have
  **zero Minecraft dependencies**; a small adapter wires them to a platform. Paper ships today;
  Fabric/Sponge can be added as additional adapters without touching the core.
- **Zero third-party runtime dependencies** — only the JDK's `ImageIO`/`AWT` and the platform API
  your server already provides.
- **Async & tick-safe** — world reads are snapshotted on the main thread (throttled); all pixel
  work, shading, drawing and PNG encoding happen on background threads.

Requires **Java 21**. The Paper adapter targets **Paper 1.21+** (Folia is not supported).

## Modules

| Module | Artifact | Contains | Minecraft dep |
| --- | --- | --- | --- |
| `worldtopng-core` | `liltrip:worldtopng-core` | renderer, `ColorPalette`/`DefaultColorPalette`, `RenderOptions`, `RenderedMap`, `MapCanvas`, `MapBuffer`, `Colors` | none |
| `worldtopng-paper` | `liltrip:worldtopng-paper` | `WorldToPng`, `RenderRequest`, Paper engine; depends on core | Paper API (`compileOnly`) |

A future `worldtopng-fabric` would depend on `worldtopng-core` and read the world via the Fabric API.

---

## Add it to your plugin

Publish the library somewhere your build can see it, then depend on the adapter for your platform.

**Build & publish locally:**

```bash
git clone <this repo> && cd WorldToPng
./gradlew publishToMavenLocal      # publishes liltrip:worldtopng-core/-paper:1.0.0 to ~/.m2
```

**Your plugin's `build.gradle`** — shade and **relocate** the library so two plugins that both
bundle it can't collide:

```groovy
plugins {
    id 'java'
    id 'com.gradleup.shadow' version '8.3.5'   // or the version matching your Gradle
}

repositories {
    mavenLocal()                                                       // or JitPack
    maven { url = 'https://repo.papermc.io/repository/maven-public/' }
    mavenCentral()
}

dependencies {
    compileOnly 'io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT'
    implementation 'liltrip:worldtopng-paper:1.0.0'   // pulls worldtopng-core transitively
}

shadowJar {
    relocate 'liltrip.worldToPng', 'com.yourplugin.libs.worldtopng'    // covers core + paper
}
tasks.named('build') { dependsOn(tasks.named('shadowJar')) }
```

> The included **WorldToPngTest** demo project shows the whole flow with a working `/maptest`
> command (it uses a simple fat jar for brevity; production plugins should relocate as above).

---

## Usage

```java
import liltrip.worldToPng.WorldToPng;
import liltrip.worldToPng.RenderRequest;
import liltrip.worldToPng.core.RenderOptions;
import liltrip.worldToPng.core.RenderedMap;

public final class MyPlugin extends JavaPlugin {

    private WorldToPng worldToPng;

    @Override public void onEnable() {
        this.worldToPng = WorldToPng.create(this);          // pass your own plugin instance
    }

    public void export(World world) {
        RenderRequest request = RenderRequest.builder(world)
                .bounds(-512, -512, 512, 512)               // inclusive block bounds, any order
                .options(RenderOptions.defaults())
                .build();

        worldToPng.render(request)
                .thenAccept(map -> map.writeTo(getDataFolder().toPath().resolve("map.png")))
                .exceptionally(ex -> { getLogger().warning("Render failed: " + ex.getCause()); return null; });
    }

    @Override public void onDisable() {
        if (worldToPng != null) worldToPng.close();         // releases background threads
    }
}
```

`render(...)` returns a `CompletableFuture<RenderedMap>`; it never blocks the caller, and the
future completes on a background thread.

> The future completes off the main thread. If your continuation touches the Bukkit API (sending
> messages, etc.), hop back with the scheduler first.

**Orientation & scale:** one pixel per block column. North (`-Z`) is up, west (`-X`) is left.

---

## Drawing / annotations

`RenderedMap.canvas()` gives a fluent `MapCanvas` (backed by `Graphics2D`) for stamping text,
images and shapes onto the map. Drawing mutates the map's image, so you can `writeTo` afterwards.
Colours are packed `0xAARRGGBB` ints. Coordinates are pixels, except the `*AtWorld` helpers which
take world block coordinates.

```java
worldToPng.render(request).thenAccept(map -> {
    try (MapCanvas c = map.canvas()) {
        c.color(0x99000000).fillRect(0, 0, map.width(), 22);          // title strip
        c.color(0xFFFFFFFF).fontSize(16f).bold().text("Spawn region", 6, 16);

        c.color(0xFFE53935).markerAtWorld(spawnX, spawnZ, 4);          // red dot at world spawn
        c.color(0xFFFFFFFF).textAtWorld("spawn", spawnX + 6, spawnZ);

        c.color(0xFF42A5F5).strokeWidth(2f)                            // a route polyline
         .polyline(new int[]{ c.toPixelX(x1), c.toPixelX(x2) },
                   new int[]{ c.toPixelZ(z1), c.toPixelZ(z2) });

        c.image(MapCanvas.loadImage(iconPath), 8, 30);                 // stamp an icon (PNG/JPG)
    }
    map.writeTo(out);
});
```

Available: `text`/`textCentered`/`textAtWorld`, `color`/`strokeWidth`/`font`/`fontSize`/`bold`,
`line`, `rect`/`fillRect`, `oval`/`fillOval`, `circle`/`fillCircle`, `polyline`/`polygon`/`fillPolygon`,
`markerAtWorld`, `image`/`imageAtWorld` (+ `MapCanvas.loadImage(Path|byte[])`).

---

## Options

`RenderOptions` (defaults via `RenderOptions.defaults()`):

| Option | Default | Effect |
| --- | --- | --- |
| `heightShading` | `true` | Brighten/darken each column relative to its northern neighbour (relief). |
| `waterDepthShading` | `true` | Darken water by depth. |
| `generateMissingChunks` | `false` | Generate ungenerated chunks (writes terrain to disk). Off by default; ungenerated columns render transparent. |
| `palette` | `DefaultColorPalette.instance()` | Block-id → colour mapping. |

Copy with the `with…` methods, e.g. `RenderOptions.defaults().withHeightShading(false)`.

### Custom colours

The palette is keyed by **namespaced block id** (so it is platform-neutral):

```java
ColorPalette palette = DefaultColorPalette.builder()
        .set("minecraft:water", 0xFF1133AA)
        .set("minecraft:grass_block", 0xFF5BA130)
        .build();

RenderOptions options = RenderOptions.defaults().withPalette(palette);
```

A colour with `alpha == 0` makes that block transparent (skipped). Implement `ColorPalette`
yourself for full control — it must be safe for concurrent reads.

---

## Tuning

`WorldToPng.create(plugin, settings)` takes a `Settings` record:

- `maxAreaBlocks` (default `16_000_000`) — refuses larger renders (`width × depth`) to guard memory
  (~a 4096×4096 image).
- `maxConcurrentChunkLoads` (default `12`) — chunks loaded/snapshotted at once.

Working memory ≈ `8 bytes × width × depth` (arrays) + `4 bytes × width × depth` (image).

---

## Ungenerated chunks

A render only includes terrain the server has **already generated**. Columns in chunks that have
never been generated are left **transparent** in the PNG (you'll see them as blank). This is
deliberate: it keeps rendering fast and read-only, and never silently grows your world.

Two ways to get a fully-filled map:

1. **Pre-generate the region first (recommended).** Run a dedicated pre-generator over the area
   once — e.g. [Chunky](https://www.spigotmc.org/resources/chunky.81534/)
   (`/chunky world <world>`, `/chunky center <x> <z>`, `/chunky radius <n>`, `/chunky start`) or a
   WorldBorder fill — then render with generation **off** (the default). Bulk pre-generators are far
   faster and gentler on the server than generating during a render, and you only pay for it once.

2. **Generate on the fly.** Tell the renderer to generate (and save) missing chunks as it walks the
   region:

   ```java
   RenderOptions options = RenderOptions.defaults().withGenerateMissingChunks(true);
   RenderRequest request  = RenderRequest.between(corner1, corner2, options);
   ```

   ⚠️ This **writes new terrain to your world** (growing the save on disk) and runs the full world
   generator, so it is slow and server-heavy for large areas. Use it only for small regions;
   otherwise prefer option 1.

> Rule of thumb: **everything in the requested range should be pre-generated** before rendering,
> unless you intentionally opt into `generateMissingChunks(true)` for a small area.

## Adding another platform (e.g. Fabric)

The core knows nothing about Minecraft. A new adapter only needs to:

1. Walk the target region's columns on the correct thread for that platform.
2. For each column, resolve the surface block's namespaced id, ask the `ColorPalette` for a colour,
   and call `MapBuffer.set(worldX, worldZ, argb, surfaceY)`.
3. Call `MapBuffer.compose(options)` to get the `RenderedMap`.

`worldtopng-paper`'s `PaperMapRenderer` is the reference implementation (~200 lines).

## Limitations / roadmap

- Paper adapter only so far (Fabric/Sponge are possible via new adapters).
- Per-block colours, not biome-tinted.
- One image per render; no built-in tiling/downscaling for very large exports yet.

## License

See the repository.
