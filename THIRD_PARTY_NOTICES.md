# Third-party notices

Wurm Waypointer is an independent clean-room implementation. No source code or
history from the legacy Waypoints mod is present in this repository, compiled,
or copied into the artifact.

Keybinder (`org.keybinder.wurm`, LGPL-3.0-or-later) was reviewed for
architecture and lifecycle principles. Wurm Waypointer does not depend on its
JAR and contains no copied Keybinder implementation source.

Wurm Unlimited client classes and the client modloader are compile-time/runtime
dependencies supplied by the user's Wurm installation. They are not bundled in
the Wurm Waypointer artifact. The decorative M-map parchment is an adapted
local UI backing based on the Wurm `map.freedom` artwork; the island depictions
and labels were removed for use behind the active server map.

On recognized Sklotopolis Liberty, Novus, Caza, and Infinity servers, the map
and navigation modules may retrieve the server operator's published flat map,
`deeds.json`, and `highways.json` at runtime. That data is validated and cached
locally but is not copied into the distribution.

The compact Sklotopolis wordmark shown on the M-map was supplied by the server
community for this integration. The Sklotopolis name and artwork remain the
property of their respective owner; inclusion here grants no broader rights.

Wurm Waypointer is licensed under the GNU Lesser General Public License version
3 only (`LGPL-3.0-only`). The distribution includes both the GNU GPL v3 terms
and the LGPL v3 additional permissions in `LICENSE` and `COPYING.LESSER`.
