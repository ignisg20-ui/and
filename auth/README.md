# ArisAuth

**Authorization plugin for Paper / Spigot / Purpur 1.21.x (Java 21).**

ArisAuth provides classic offline-mode-style account protection: every player
who joins is frozen, sees a title + boss-bar + action-bar prompt, and must
`/register` or `/login` before they can do anything. Returning players within
a configurable session window skip the prompt entirely.

## Features

- `/register <pass> <repeat>` (aliases: `/reg`), `/login <pass>` (aliases: `/l`), `/changepassword <old> <new>` (aliases: `/changepass /cp`), `/auth <reload|status|session>`
- bcrypt password hashing (configurable cost factor)
- SQLite (default) or YAML user store, fully async
- Sticky 5-minute session that survives reconnects from the same IP
- Pre-auth freeze: movement, blocks, inventory, chat, drops, pickups, combat - everything blocked except the auth commands
- Full damage immunity until authenticated (void, fall, fire, lava, suffocation, drown, explosion, lightning, cactus, potions, knockback)
- 60-second timeout (configurable) with auto-kick
- BossBar countdown + ActionBar reminder every 2s + animated title + portal-particle ring + sound cues
- Brute-force throttle (5 attempts -> 10 minute IP lockout, both configurable)
- Hot-reload via `/auth reload`

## Installation

1. Place `arisauth-1.0.0.jar` in your server's `plugins/` directory.
2. Start the server once. The default `plugins/ArisAuth/config.yml` is created.
3. Edit any text under `messages:`, swap storage to YAML if you prefer, or tune `general.session-seconds`.
4. `/auth reload` to apply changes without restarting.

## Configuration highlights

```yaml
general:
  session-seconds: 300          # 5 minutes - skip /login if rejoined within this
  auto-login-on-register: true  # new accounts skip /login immediately
  login-timeout-seconds: 60     # kick if not logged in within this many seconds

storage:
  type: "sqlite"   # or "yaml"

brute-force:
  max-attempts: 5
  lockout-seconds: 600
```

The full default config is included in the jar (`config.yml`) and is copied to
`plugins/ArisAuth/config.yml` on first run.

## Commands

| Command                            | Description                                       |
|------------------------------------|---------------------------------------------------|
| `/register <pass> <repeat>`        | Create an account                                 |
| `/login <pass>`                    | Authenticate (skipped if the session is still hot) |
| `/changepassword <old> <new>`      | Change password (must already be logged in)       |
| `/auth reload`                     | Re-read `config.yml`                              |
| `/auth status`                     | Live overview (online / sessions / storage)       |
| `/auth session <player>`           | Inspect a player's session entry                  |

Allowed commands while unauthenticated: `/login /l /register /reg /changepassword /changepass /cp`. Everything else is blocked.

## Building from source

```bash
./gradlew shadowJar
```

The runnable plugin lives at `build/libs/arisauth-1.0.0.jar`.
