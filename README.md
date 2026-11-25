<!-- credits:start -->
> **Credits & license.** Port and maintenance by [JackStar6677-1](https://github.com/JackStar6677-1) and [DrakesCraft Labs](https://github.com/SlimefunNewHorizons). Original authors: [espidev](https://github.com/espidev), [Dragoboss](https://github.com/Dragoboss), [vik1395](https://github.com/vik1395), [jerzean](https://github.com/jerzean), [Krakenied](https://github.com/Krakenied), [dylanpdx](https://github.com/dylanpdx), [chick4nnn](https://github.com/chick4nnn), [flutz1](https://github.com/flutz1), [antonkesy](https://github.com/antonkesy), [itaquito](https://github.com/itaquito), [mikroskeem](https://github.com/mikroskeem), [rinpr](https://github.com/rinpr), EspiDev (plugin.yml). Upstream license: **GNU General Public License v3.0** (see [LICENSE](LICENSE)). Details: [docs/UPSTREAM_ATTRIBUTION.md](docs/UPSTREAM_ATTRIBUTION.md) · Policy: [Rights & Attribution](https://github.com/SlimefunNewHorizons/.github/blob/main/RIGHTS_AND_ATTRIBUTION.md). Independent community project, not affiliated with or endorsed by the original authors or the official Slimefun team.
>
> **Créditos y licencia.** Port y mantenimiento por [JackStar6677-1](https://github.com/JackStar6677-1) y [DrakesCraft Labs](https://github.com/SlimefunNewHorizons). Autores originales: [espidev](https://github.com/espidev), [Dragoboss](https://github.com/Dragoboss), [vik1395](https://github.com/vik1395), [jerzean](https://github.com/jerzean), [Krakenied](https://github.com/Krakenied), [dylanpdx](https://github.com/dylanpdx), [chick4nnn](https://github.com/chick4nnn), [flutz1](https://github.com/flutz1), [antonkesy](https://github.com/antonkesy), [itaquito](https://github.com/itaquito), [mikroskeem](https://github.com/mikroskeem), [rinpr](https://github.com/rinpr), EspiDev (plugin.yml). Licencia original: **GNU General Public License v3.0**. Detalle en [docs/UPSTREAM_ATTRIBUTION.md](docs/UPSTREAM_ATTRIBUTION.md).
<!-- credits:end -->

> **Branches / Ramas.** `main`: Paper/Purpur 1.21.11 (Java 21). `port-26x`: Paper/Purpur 26.x (production: Purpur 26.2, Java 25). If another section of this README says otherwise, this note prevails. / Si otra seccion de este README dice otra cosa, prevalece esta nota.

![ProtectionStones](/logo.png?raw=true)

[![Maven Central](https://img.shields.io/maven-central/v/dev.espi/protectionstones.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22dev.espi%22%20AND%20a:%22protectionstones%22)
![Open issues](https://img.shields.io/github/issues-raw/espidev/ProtectionStones)
![Closed issues](https://img.shields.io/github/issues-closed-raw/espidev/ProtectionStones)

[Spigot](https://www.spigotmc.org/resources/protectionstones-updated-for-1-13-1-16-wg7.61797/) | [Permissions](https://espidev.gitbook.io/protectionstones/permissions) | [Commands](https://espidev.gitbook.io/protectionstones/commands) | [Configuration](https://espidev.gitbook.io/protectionstones/configuration) | [Placeholders](https://espidev.gitbook.io/protectionstones/placeholders) | [Translations](https://espidev.gitbook.io/protectionstones/translations) | [API Information](https://espidev.gitbook.io/protectionstones/api) | [Javadocs](https://jdps.espi.dev/) | [Dev Builds](https://ci.espi.dev/job/ProtectionStones/)

Get support for the plugin on the M.O.S.S. Discord! https://discord.gg/cqM96tcJRx

ProtectionStones is a grief prevention and land claiming plugin.

This plugin uses a specified type of minecraft block/blocks as a protection block. When a player placed a block of that type, they are able to protect a region around them. The size of the protected region is configurable in the plugins config file. You can also set which flags players can change and also the default flags to be set when a new region is created.

View the Spigot page (with FAQ and install instructions) [here](https://www.spigotmc.org/resources/protectionstones-updated-for-1-13-1-16-wg7.61797/).

Check the [wiki](https://github.com/espidev/ProtectionStones/wiki) for plugin reference information.

### Dependencies
* ProtectionStones 2.10.6
  * Spigot 1.21.6+
  * WorldGuard 7.0.9+
  * WorldEdit 7.2.6+
  * Vault (Optional)
  * PlaceholderAPI (Optional)
  * LuckPerms (Optional)

### Building
Make sure you have the Java 21 JDK installed, as well as Maven.

```
git clone https://github.com/espidev/ProtectionStones.git
cd ProtectionStones
mvn clean install
```

Compiling ProtectionStones will also produce a jar with JavaDocs, which can be useful if you need documentation for an older version.

### Usage Statistics
<img src="https://bstats.org/signatures/bukkit/protectionstones.svg">

View full usage statistics [here](https://bstats.org/plugin/bukkit/ProtectionStones/4071).

This plugin is licensed under the **GPLv3**, as is required by Bukkit plugins.
