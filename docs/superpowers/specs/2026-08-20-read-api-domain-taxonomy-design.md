# Read API Domain Taxonomy

**Status:** Approved

**Date:** 2026-08-20

## Purpose

Define the complete working catalog of general OSRS read-API domains before designing methods for any domain. This catalog establishes ownership and scope; it does not specify accessors, data models, or implementation order.

## Domain rule

A domain owns a coherent body of game state with an independent lifecycle and vocabulary. A domain is not:

- an individual API method;
- a widget, varp, varbit, varc, client script, or cache record;
- a boss, raid, minigame, location, encounter, or training method; or
- an implementation detail of RuneLite or the game client.

The catalog includes foundational systems used across the game, the 24 skills, and supporting client-visible world and interaction state. Activity-specific APIs are outside the current expansion scope.

## Canonical domain catalog

### Player and runtime

- **Session** — login/game availability and session lifecycle.
- **Account** — account mode and safe account-level properties. Credentials, tokens, remembered login identity, and other sensitive authentication data are excluded.
- **Player** — the currently loaded local avatar, identity, appearance, combat level, and generic actor state.
- **World** — connected world number, world types, and server/world metadata.
- **Location** — world and local coordinates, plane, region, world view, and instance mapping.
- **Movement** — run/walk state, run energy, weight, movement state, and destination when available.
- **Transportation** — general travel-network state and unlocks that are not owned by one activity.
- **Status Effects** — general buffs, debuffs, poison, venom, disease, stamina, teleblock, and similar timed conditions.
- **Settings** — game-owned preferences. RuneLite plugin configuration is excluded.

### Items

- **Inventory** — the slot-preserving 28-slot backpack snapshot.
- **Equipment** — worn items keyed by their semantic equipment slots.
- **Item Definitions** — static client/cache-visible facts about an item ID, including identity, actions, stackability, tradeability, notes, and placeholders.
- **Item Condition and Charges** — dynamic charge, degradation, imbue, and similar per-item state that is not part of static item identity.
- **Portable Containers** — a family for carried containers such as the Rune Pouch, Looting Bag, and Seed Box. Each container may require its own state model and availability rules.

### Storage

- **Bank** — personal bank contents and bank-specific organization state.
- **Seed Vault** — personal seed and sapling storage.
- **Group Storage** — shared Group Ironman storage.
- **Potion Storage** — the bank-adjacent potion ledger, which is not an ordinary bank item container.
- **Player-Owned House Storage** — persistent storage owned by Construction/POH systems.
- **Death and Recovery** — kept-item state, gravestones, Death's Office, Death's Coffer, and general recovery state.

### Character systems

- **Skills** — every skill's real level, current/boosted level, experience, and aggregate total level.
- **Combat** — active engagement, target, attack style, auto-retaliate, special attack, and general combat state.
- **Prayer** — prayer book, active prayers, quick-prayer configuration, and quick-prayer state.
- **Magic** — spellbook, spell filtering, selected spell, autocast, and other general magic state.

`Skills` is the source of truth for levels and experience. Combat, Prayer, Magic, and individual skill domains may reference skill values but should not duplicate ownership of them.

### Progression

- **Quests and Miniquests** — canonical identity, completion state, and quest-point progress. Quest-step helpers and route inference are excluded.
- **Achievement Diaries** — region, tier, task-completion, and reward-claim state.
- **Combat Achievements** — task completion, points, tiers, and reward thresholds without encounter evaluation.
- **Collection Log** — acquisition records rather than current item possession.
- **Adventure Paths** — account progression through the official path system.
- **Music** — music-track unlock and playback state.
- **Emotes** — emote availability and current emote state.

### Commerce

- **Grand Exchange** — persistent slot-indexed offers and collection state.
- **Shops** — the currently loaded NPC-shop session and stock.
- **Player Trade** — the current two-party offer and confirmation session.

Grand Exchange offers persist beyond the open interface. Shops and Player Trade are interface-scoped sessions and must expose availability explicitly.

### Social and communication

- **Friends and Ignores** — friend and ignore lists plus client-known online/world state.
- **Friends Chat** — joined channel, owner, local rank, and channel members.
- **Clan** — ranked clan, guest clan, members, ranks, titles, and readable clan settings.
- **Account Group** — persistent account-group membership and roster state, distinct from shared item storage.
- **Chat** — messages, channels, filters, privacy modes, and selected input channel, subject to privacy review.

### Loaded world state

- **Scene and Collision** — loaded tiles, terrain, bridges, collision, world views, and instances.
- **Other Players** — non-local player entities in loaded world views.
- **NPCs and Followers** — loaded NPC entities and the local player's current follower.
- **Tile Objects** — walls, ground objects, decorative objects, and game objects.
- **Ground Items** — loaded tile items, quantities, ownership, and visibility state.
- **Projectiles and Graphics** — transient projectile and graphics-object state.

Loaded-world domains describe only what the client currently knows. They must not imply global or persistent knowledge outside loaded world views.

### Interaction and presentation

- **Interfaces** — open interface/modal topology and semantic interface availability.
- **Dialogue and Input** — dialogue, Make-X, chatbox prompts, and text/number input state.
- **Selection and Menu** — selected item/spell/target and current context-menu state.
- **Camera and Viewport** — camera orientation, focus, zoom, and viewport state.
- **Minimap and World Map** — minimap orientation and current world-map view state.

These are valid read surfaces but rank below durable gameplay domains for implementation unless a higher-level domain depends on them.

## Skill domains

The 24 named skills are also domains because each owns mechanics beyond its level and experience:

1. Agility
2. Attack
3. Construction
4. Cooking
5. Crafting
6. Defence
7. Farming
8. Firemaking
9. Fishing
10. Fletching
11. Herblore
12. Hitpoints
13. Hunter
14. Magic
15. Mining
16. Prayer
17. Ranged
18. Runecraft
19. Sailing
20. Slayer
21. Smithing
22. Strength
23. Thieving
24. Woodcutting

Magic and Prayer are cross-listed rather than duplicated: their level and experience remain in `Skills`, while their active mechanics belong to the single `Magic` and `Prayer` domains.

## Ownership and lifecycle rules

- Prefer the most specific domain as the source of truth; broad snapshots may reference but should not duplicate it.
- Preserve slot identity, raw item IDs, quantities, notes, placeholders, charged variants, and other meaningful raw distinctions.
- Distinguish `unavailable`, `not loaded`, `loaded empty`, and `last observed`; never collapse them into one empty result.
- Character Summary is an aggregate read model over Skills, Quests, Diaries, Combat Achievements, Collection Log, and play time, not a separate source-of-truth domain.
- Physical currencies are items. Virtual balances have unrelated lifecycles and do not form one universal Currency domain.
- Item possession, Grand Exchange offers, Collection Log acquisition, shop stock, and trade offers are separate facts.
- Generic source structures such as `Client`, `Actor`, `ItemContainer`, widgets, and var stores may support multiple domains but do not define public domain seams.
- Public snapshots should be immutable and explicitly represent unknown or unavailable state.

## Current exclusions

- Minigames, bosses, raids, encounters, and their rewards or phase state.
- Individual locations, training methods, routes, and content-specific helpers.
- Mutation, menu interaction, automation, pathfinding, recommendations, and encounter advice.
- XP-rate estimation, damage calculators, castability solvers, prayer timing, and inferred state presented as authoritative client state.
- Credentials, authentication material, remembered login identity, server addresses, and unreviewed sensitive account/chat data.
- A universal storage, currency, wealth, loot-history, or portable-container abstraction that erases source-specific lifecycles.

Existing activity-specific APIs are not removed by this decision; they are simply outside the current expansion roadmap.

## Method-design phase

Method brainstorming will happen domain by domain. For each domain, the design should establish:

1. the state it owns and the neighboring domains it references;
2. authoritative RuneLite, cache, and interface sources;
3. availability and lifecycle semantics;
4. immutable snapshot fields;
5. useful derived read methods that add meaning beyond native RuneLite calls;
6. edge cases and sentinel behavior; and
7. the smallest representative verification needed.

No implementation should begin merely because a domain appears in this catalog. A domain should be implemented when its methods and added value have been agreed.
