# `/ps view` particle candidate — 2026-11-19

The purple claim marker used dust size `10`. Two production scheduler warnings
reported `IllegalArgumentException` with the accepted interval `[0.01,4.0]`.
The downloaded production `ArgView.class` also contains the `10.0f` literal.
The candidate changes only that size to `4`; pink/cyan colors, scheduling,
distance limits, permissions and claim data keep their existing behavior.

The same one-line correction is present on the legacy default branch `drake`
and the development branch `port-26x`. ProtectionStones remains a separate
plugin; no equivalent implementation was located in Drakes-Suites.

`ArgViewParticlesTest` exercises the three real marker handlers with a local
player/scheduler fixture: emitted dust sizes/colors, thirty scheduled emissions,
distance/height rejection and offline suppression. **It has not executed yet.**

Two protected Maven attempts failed before tests:

1. Java 21, offline: uncached `nexus-staging-maven-plugin:1.6.13` build extension.
2. Java 21, online: WorldEdit `7.4.4` classes have bytecode major `69` (Java 25),
   while the compiler accepts major `65`. This includes `BlockVector3` and
   `BukkitAdapter`; the later missing-symbol diagnostics follow that mismatch.

No third build was attempted, in accordance with the two-failure stop rule.
Resume by selecting a coherent toolchain/dependency set for each target;
compiling with Java 25 alone does not prove Java 21 runtime compatibility.
Then run `mvn clean package` through `build_seguro.sh`, execute the regression,
and exercise `/ps view` on the target server before claiming runtime support.

**Status: source candidate only, not BUILT, STAGED or ACTIVE.** No production
jar was uploaded and no server was restarted. A deployment still requires
artifact provenance, backup, matching local/remote size and SHA-256, and the
normal orchestrator/restart gates. Revert the one-line commit to roll back
the candidate; no live state needs restoration.

The Paper API describes dust as color and relative size; its constructor alone
does not validate this server-side limit. The regression checks emitted options,
not a successful client render. See [DustOptions API](https://jd.papermc.io/paper/1.21.11/org/bukkit/Particle.DustOptions.html).
