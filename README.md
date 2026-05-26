# Shield

**Production-ready security plugin for Paper / Spigot / Purpur 1.21.x (Java 21).**

Shield bundles ten cooperating modules that protect a Minecraft server against
DDoS, bots, exploits, cheating clients, X-ray, crash-vectors, OP-escalation and
console abuse. Every module is independently configurable, hot-reloadable, and
designed to keep the false-positive rate low enough for whitelist-only and
public servers alike.

> Tested target: Paper 1.21.4 on Java 21. Compatible with Spigot and Purpur of
> the same version. Folia is **not** supported (the plugin uses the legacy
> scheduler).

## Features at a glance

| Module          | What it does                                                                 |
|-----------------|------------------------------------------------------------------------------|
| Anti-DDoS       | Per-IP and global handshake / login rate-limits, reconnect-spam scoring, auto-ban |
| Anti-Bot        | Keyword captcha (default: type `.arisworld` in chat), fake-session detection, join-flood guard, quarantine |
| Anti-Exploit    | NBT/book/sign/creative/command/tab-complete/chunk/entity filters             |
| Anti-Cheat      | KillAura, Reach, Speed, Fly, NoFall, Scaffold, AutoClicker, AimAssist, Timer, Velocity, Jesus |
| Anti-Xray       | Behavioural mining-pattern detector (uses Paper's native obfuscator)         |
| Anti-Crash      | Packet/chunk/entity/redstone/hopper soft caps                                |
| Anti-OP         | Snapshot diff + automatic rollback for unauthorised `op`                     |
| Anti-Console    | Dangerous-command sandbox with `--shield-confirm` gating                     |
| Logs            | Async rotating security log + Discord & Telegram webhooks                    |
| Admin command   | `/shield status banip whitelist scan quarantine release debug reload`        |

## Installation

1. Make sure your server is running Paper, Spigot or Purpur for **Minecraft
   1.21.x** on **Java 21**.
2. Download the latest `shield-x.y.z.jar` from the [Releases](../../releases)
   page (or build it yourself - see below).
3. Drop the jar into the `plugins/` directory.
4. Start (or restart) the server once so the default `config.yml` is written
   to `plugins/Shield/config.yml`.
5. Adjust the configuration if needed and run `/shield reload`.

That is the whole installation flow - **no dependencies are required**. Paper
already ships with Netty and Bukkit, both of which Shield uses as
`compileOnly` libraries during build time only.

## Building from source

The repository ships a Gradle wrapper, so no system-wide Gradle is required.
You only need a working JDK 21:

```bash
./gradlew shadowJar
```

The runnable plugin jar will appear at `build/libs/shield-1.0.0.jar`.

## Configuration

The defaults are tuned for a typical 1.21 survival server. The most useful
top-level switches are:

```yaml
general:
  observe-only: false       # set true to log violations without blocking
  bypass-permission: "shield.bypass"

anti-bot:
  captcha:
    enabled: true
    type: "keyword"          # keyword | math | letters
    keyword: ".arisworld"    # what the player must type in chat (no /)
    timeout-seconds: 60
    max-attempts: 3

# behavioural anti-cheat is OFF by default — turn it on only if you want
# the KillAura/Reach/Speed/etc. checks in addition to the keyword captcha
anti-cheat:
  enabled: false
  actions:
    warn-at: 5
    alert-at: 10
    kick-at: 25
    ban-at: 60
```

The keyword captcha is the primary verification: on every join Shield asks
the player to write the configured phrase **as a regular chat message** (no
leading `/`). They have `timeout-seconds` to do so and `max-attempts` retries
before being kicked. Change `keyword:` to whatever you want.

If you also want the behavioural anti-cheat (KillAura/Reach/Speed/Fly/...),
set `anti-cheat.enabled: true`. Each individual check has its own sensitivity
or limit you can tune.

### Webhooks

Discord:

```yaml
webhook:
  discord:
    enabled: true
    url: "https://discord.com/api/webhooks/...."
```

Telegram:

```yaml
webhook:
  telegram:
    enabled: true
    bot-token: "123456:ABC..."
    chat-id: "-1001234567890"
```

Only alerts at or above `minimum-severity` (default `MEDIUM`) are forwarded.

### Commands

| Command                                            | Action                              |
|----------------------------------------------------|-------------------------------------|
| `/shield status`                                   | Live overview of every module       |
| `/shield banip <ip> [seconds] [reason...]`         | Manual IP block                     |
| `/shield unbanip <ip>`                             | Remove a manual IP block            |
| `/shield whitelist <on\|off\|add\|remove\|list>`   | Manage the vanilla whitelist        |
| `/shield scan`                                     | Snapshot of suspicious live players |
| `/shield quarantine <player> [seconds] [reason]`   | Isolate a player                    |
| `/shield release <player>`                         | Lift a quarantine                   |
| `/shield debug`                                    | Print runtime diagnostics           |
| `/shield reload`                                   | Re-read `config.yml` (hot reload)   |

All sub-commands require the `shield.admin` permission (granted to OPs by
default).

## Performance

* Every IO-heavy operation (logging, webhook delivery, sweeps) runs on
  dedicated daemon executors.
* Per-player state lives in plain `ConcurrentHashMap`s with lock-free
  collectors so hot paths never block the main thread.
* The packet interceptor uses a single Netty handler per channel and falls
  back gracefully when the underlying server fork hides its network channel.
* When `general.observe-only: true` is set Shield only logs - useful for
  benchmarking against false positives in production environments.

## Avoiding false positives

* **Anti-cheat warmup**: checks stay disabled for `warmup-seconds` after a
  player joins so loading delays cannot be misread as cheating.
* **Per-check decay**: scores naturally decay over `score-decay-seconds` so
  one bad sample never escalates into a ban.
* **Bypass permission**: give `shield.bypass` to staff testing or builders to
  fully silence the engine for them.
* **Sensitivity knobs**: every check has its own threshold; tune individual
  modules instead of toggling whole categories off.

## Reporting issues

Please open an issue on this repository with:

1. Your server jar/version (`/version`)
2. Java version (`java -version`)
3. Relevant portion of `plugins/Shield/logs/security.log`
4. Relevant `config.yml` changes from defaults

## License

Released as open source. See the project repository for license details.
