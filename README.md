# RaindanceMC/ranks

- **`ranks-module`** — Rain's Flexible Modules — Ranks: Ranks shown in front of a name — [OWNER], [DEV], [ADMIN] — in chat, above the head and in the tablist, each place switchable for the whole server and by every player for their own.
- **`ranks-standalone`** — Rain's Ranks: Rain's Ranks, as a plugin of its own: the ranks module and the standard wrapper, shaded together.

Part of [RaindanceMC](https://github.com/RaindanceMC); built on [RainsCore](https://github.com/RaindanceMC/RainsCore) and the shared parent in [framework](https://github.com/RaindanceMC/framework). Split out of `rains-flexible-modules` on 16.10.2026, history kept.

## Build

```
mvn install
```

Needs Java 25 and read access to `packages.tstieh.de` (server ids `packages-releases` / `packages-snapshots` in `~/.m2/settings.xml`).

## Plugins

- `ranks-standalone/target/RainsRanks-0.5.1.jar` — also published as `de.raindancer.modules:ranks-standalone:0.5.1`

## Versions

A module's version is a property in this repository's root `pom.xml`, a plugin's is `plugin.version` in its own pom. CI publishes every version that is not in the registry yet on the next push to master.
