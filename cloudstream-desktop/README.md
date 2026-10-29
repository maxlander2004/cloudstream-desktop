# CloudStream Desktop

A native desktop build of [CloudStream](https://github.com/recloudstream/cloudstream) using
Compose Multiplatform. It installs and runs like a normal Windows program: double-click the
shortcut, a window opens. No emulator, no Windows Subsystem for Android, no Android runtime
of any kind — `jpackage` bundles a Java runtime and a native launcher into a single installer.

Licensed GPL-3.0-or-later, like upstream. This is a fork; it is not affiliated with or endorsed
by the CloudStream project.

## Build an installer

Requires a JDK 21 and nothing else — no Android SDK, no Android Studio. The `:cs-compat`
module compiles the checked-in `src-cloudstream/library` sources, so keep that folder in the
repo. On Windows, building an `.msi` or `.exe` additionally requires
[WiX Toolset v3](https://github.com/wixtoolset/wix3/releases) on `PATH` (`jpackage`
uses it to build Windows installers).

```bash
# Windows — run on Windows
gradlew.bat packageMsi     # build/compose/binaries/main/msi/*.msi
gradlew.bat packageExe     # build/compose/binaries/main/exe/*.exe

# Linux
./gradlew packageDeb

# Run from source without packaging
./gradlew run
```

`./gradlew createDistributable` produces an unpacked app image with the native launcher
(`build/compose/binaries/main/app/CloudStream/bin/CloudStream`).

MSI/EXE packaging is Windows-only: `jpackage` cannot cross-compile them. The
`desktop-installers.yml` workflow builds both on a `windows-latest` runner and uploads them
as artifacts, so a Windows machine is not strictly required.

## Data locations

| Path | Contents |
| --- | --- |
| `~/.cloudstream-desktop/settings.properties` | player command and other settings |
| `~/.cloudstream-desktop/extensions/` | installed extensions (`.cs3`, `.cs`, `.apk`, `.jar`) |
| `~/.cloudstream-desktop/dex-cache/` | translated `classes.dex` → JVM bytecode, keyed by content hash |

## Extensions

Two things can provide content, and neither is built in: like the Android app, a fresh
install has no providers until an extension is added.

1. **Original CloudStream extensions** — the `.cs` / `.cs3` archives from the official
   [extension repository](https://github.com/recloudstream/extensions), used as-is. See below.
2. **Desktop extensions** — `.jar` files written against this app's `Catalog` API.

### Original CloudStream extensions

Published extensions ship Dalvik bytecode, which the JVM cannot execute directly, so the app
loads them in three steps:

1. `classes.dex` is translated to JVM class files with
   [dex2jar](https://github.com/Fmstrat/dex2jar) (cached by file hash).
2. The plugin is loaded exactly like `PluginManager.loadPlugin` does: `manifest.json` is read
   from the archive, `pluginClassName` is instantiated as a `BasePlugin`, and `load()` is
   called. Providers register themselves through `registerMainAPI` into `APIHolder`.
3. Each registered provider becomes a source in the sidebar, wrapping the same calls the
   Android app makes — `getMainPage`, `search`, `load`, `loadLinks`.

Crucially, the extension's bytecode is linked against a **JVM build of upstream's own
`library` module** (`:cs-compat`, compiled unmodified from `src-cloudstream/library` with the
same Kotlin version upstream uses). That is what makes `com.lagradost.cloudstream3.*` symbols
resolve for bytecode compiled against the Android variant, and it is why the real extension
ecosystem works rather than a re-implementation of it. The YouTube extractor also needs
NewPipe initialised with a `Downloader`, which the app does at load time, like
`CommonActivity` does on Android.

```bash
./gradlew verifyLegacy     # downloads 5 providers from the official repo and runs them
```

`verifyLegacy` exercises the whole path: translation, plugin load, then browse, search and
link resolution for each provider. It also fetches the official repository index and installs
every advertised extension through the repository path, SHA-256 verified. Last run against
the official repository:

| Provider | browse | search | stream links |
| --- | --- | --- | --- |
| Internet Archive | 26 items | 26 results | 4 |
| Twitch | 20 items | 38 results | 6 |
| Invidious | 40 items | 0 results* | 0* |
| YouTube | 0 items* | 20 results | — |
| Dailymotion | 26 items | 26 results | 0* |

\* Source-side problems, identical on Android: `Dailymotion`'s own extractor in the library
still models `subtitles.data` as an object while the API now returns an array, the public
Invidious instance used by default returns no search hits, and the YouTube provider's home
row is empty without a sign-in. None of them are caused by the desktop loader.

What does *not* work, and cannot without rework: extensions that need Android-only services.
`WebView`/`Cloudflare` bypass (`WebViewResolver` is a no-op on the JVM), `Resources`, and
Android `Context` access. Series and anime providers currently resolve links for the **first
episode** only — episode selection is not in the UI yet.

### Installing an extension

The Extensions screen reads CloudStream repositories the way the Android app does — a
`repo.json` manifest, its plugin lists, and the `.cs3` files they advertise — and lists every
entry with **Install** / **Update** buttons, grouped by the repository it came from and
filterable. The default index is the official
[recloudstream/extensions](https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json)
repository; Settings → **Extension repositories** takes a list of `repo.json` urls (one per
line), and `cloudstreamrepo://` / `https://cs.repo/` shortcuts are accepted like upstream.

Shortcodes work like on Android: type `megarepo` in the repository field and the app resolves
it through the same redirect service, then expands it to every repository in the community
[repos database](https://github.com/recloudstream/cs-repos) — the effect of the Mega plugin
on Android, done natively here, since that plugin only exists to add repositories and is
built against Android-only app internals. Last run: 28 repositories, 496 extensions. Most
community extensions are older builds that target the retired Android-only plugin API; like
the current Android app, the desktop host only loads `BasePlugin`-era extensions, and an
archive that does not load is removed again with the reason shown.

A download is verified against the repository's published SHA-256 and size before it lands in
`~/.cloudstream-desktop/extensions`, then extensions reload; an entry whose repository
`version` is higher than the installed one shows **Update**, and entries the repository marks
down are still listed.

Extensions → **Install file…** still accepts a manually picked `.cs3`, `.cs`, `.apk` or
`.jar`, **Install from URL…** takes a link to one, and dropping files into the extensions
folder and pressing **Reload** works too. The copy is only kept if it actually loads, so a
broken archive is reported instead of piling up. Installed sources appear as chips on the
Home screen, tagged `CloudStream` in the extensions list when they come from an original
archive.

Installed extensions behave like the Android app's: the switch enables or disables one
without deleting it (a disabled extension stays on disk and keeps its version, but registers
no providers), the bin icon removes it outright, and **Update all** applies every newer
version the repositories offer. An entry a repository marks as `Down` is installed disabled,
which is how upstream treats a provider that has gone offline.

### Desktop (.jar) extensions

A desktop extension is a jar containing:

```
manifest.json                       { "name": "...", "version": 1, "pluginClassName": "com.example.MySource" }
com/example/MySource.class          implements app.cloudstream.desktop.api.Catalog
```

`ExtensionHost` reads `manifest.json` from inside the archive and instantiates
`pluginClassName` with a `URLClassLoader`, the direct counterpart of
`dalvik.system.PathClassLoader`. Both kinds of extension end up as the same `Catalog` in the
UI, so nothing else in the app needs to know which one a source came from.

### The example extension

[`extensions/archive-org`](extensions/archive-org) is a working extension: *Public Domain
Movies*, public-domain feature films from the Internet Archive. It implements all three parts
of the API — browse, search, and stream resolution (it picks the MP4 derivatives archive.org
generates, largest first).

```bash
./gradlew :extensions:archive-org:jar          # build/extensions/archive-org/libs/archive-org-1.0.0.jar
./gradlew :extensions:archive-org:installExtension   # copy it into the extensions folder
./gradlew verifyExtension                      # load it through ExtensionHost and hit the live API
```

`verifyExtension` is the end-to-end check: it loads the installed jar through the real loader,
then runs browse, search and stream resolution against archive.org and prints the results.

### Writing your own

Add a module like `extensions/archive-org`, depending on `:plugin-api`:

```kotlin
dependencies {
    // compileOnly: the app provides these at runtime through the parent class loader
    compileOnly(project(":plugin-api"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}
```

```kotlin
package com.example

import app.cloudstream.desktop.api.Catalog
import app.cloudstream.desktop.api.CatalogItem
import app.cloudstream.desktop.api.StreamLink

class MySource : Catalog {
    override val name = "My Source"
    override suspend fun browse() = listOf(CatalogItem("Example", "https://example.com/watch"))
    override suspend fun streams(item: CatalogItem) = listOf(StreamLink("Direct", item.url))
}
```

Keep dependencies `compileOnly`. Bundling the API or the JSON library into the jar gives the
extension its own `Catalog` class object, and the loader's cast then fails with a
`ClassCastException`. The extension's parent class loader is the app's, so anything the app
ships is already available.


## Playback

Playback is delegated to an external player rather than re-implementing ExoPlayer, following
upstream's own guidance that each platform implements playback natively. Set a command in
Settings, for example:

```
mpv
C:\Program Files\VideoLAN\VLC\vlc.exe
```

With no command configured, stream URLs open in the default browser.

Sources that require a `Referer` (most extension scrapers) get it forwarded: `mpv` receives
`--http-header-fields=…` and VLC `--http-referrer=…`. Players that take no headers still get
the bare URL.

## Layout

```
plugin-api/                        Catalog / CatalogItem / StreamLink; what extensions compile against
cs-compat/                         upstream's library module compiled for the JVM (extension ABI)
extensions/archive-org/            example desktop extension (public-domain films on archive.org)
src/main/kotlin/app/cloudstream/desktop/
  Main.kt                  window entry point
  core/ExtensionHost.kt    extension loader for both formats + install/validate
  core/ExtensionRepository.kt    repositories + shortcodes (megarepo) + sha256-verified downloads
  core/legacy/DexTranslator.kt    classes.dex -> JVM bytecode (dex2jar), cached by hash
  core/legacy/LegacyExtensions.kt original .cs3/.cs/.apk plugin loader (BasePlugin + APIHolder)
  core/legacy/LegacyCatalog.kt    MainAPI provider -> desktop Catalog
  core/legacy/NewPipeDownloader.kt  NewPipe init the YouTube extractor requires
  core/Http.kt             JDK HTTP client wrapper
  core/Player.kt           external player + settings
  ui/Theme.kt              upstream dark palette
  ui/App.kt                navigation, browse, search, extensions, settings
```

`cs-compat` does not copy or rewrite anything: it points its source sets at
`../src-cloudstream/library/src` and compiles `commonMain`, `jvmCommonMain` and `jvmMain`
into a JVM artifact. Upstream's Android build is untouched.
