# Named Entity IDs and Inventory Slot Positions

## Goal

Make read snapshots self-describing: every emitted item, object, or NPC ID is paired with its canonical client name, and every inventory slot entry carries its zero-based slot number.

## Scope

This change covers the active non-minigame read APIs. It adds item names to Inventory, Equipment, and Bank. Anvil and Furnace already emit product names and retain their existing shapes. The current scoped APIs emit no public object or NPC IDs, so this design records the required sibling-name convention for future domains without adding speculative types.

Minigame APIs remain excluded. Structural IDs—including region, world-view, sprite, slot, and tab IDs—remain numeric-only.

## Public contract

`ItemSlot` will expose:

- `slot`: zero-based container slot;
- `itemId`: exact raw item ID;
- `itemName`: canonical item-composition name, or `null` for empty/unresolved IDs;
- `quantity`: raw quantity.

Inventory uses the backpack array index for `slot`. Equipment uses `EquipmentInventorySlot.getSlotIdx()`.

`BankSlot` keeps its existing `slot`, `tabNumber`, and `positionInTab` fields and adds `itemName` beside `itemId`.

Future public object and NPC records must pair `objectId` with `objectName` and `npcId` with `npcName`. A generic `{id, name}` wrapper is intentionally not introduced.

## Data flow

Each domain reads the raw container on the client thread. For each valid item ID, it immediately resolves `client.getItemDefinition(itemId).getName()` and copies that string into the immutable snapshot. Empty or unresolved IDs retain their raw ID and receive a null name. No mutable client composition is retained.

State Inspector will serialize null fields explicitly so an emitted entity ID always has a visible sibling name field, including empty sentinels.

## Compatibility

Raw IDs and quantities remain unchanged and authoritative. Inventory and Equipment keep returning `ItemSlot`; Bank keeps returning `BankSlot`. `ItemSlot` construction changes to require the captured slot and name because unnamed or positionless snapshots would violate the new contract.

## Edge cases

- Empty slots retain their sentinel item ID and expose `itemName: null`.
- Definition lookup returning no composition exposes `itemName: null` without changing the ID.
- Duplicate item IDs repeat the same canonical name at each distinct slot.
- Inventory trailing empty slots may remain absent when RuneLite omits them from the materialized container; emitted entries still report their correct raw indexes.
- Equipment empty slots still emit their semantic numeric slot indexes.

## Verification

- Extend Inventory tests for item names, explicit indexes, duplicate IDs, and empty sentinels.
- Extend Equipment tests for named occupied entries and indexed empty entries.
- Extend Bank tests for names alongside existing absolute/tab positions.
- Extend State Inspector tests to confirm null names remain visible in JSON.
- Run targeted self-tests, the complete Gradle check suite, diff validation, and development-client hot reload.
