# Named Entity IDs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pair every emitted non-minigame item ID with its canonical item name and add explicit zero-based slot numbers to Inventory and Equipment item entries.

**Architecture:** Resolve item names from RuneLite `ItemComposition` while each immutable snapshot is captured on the client thread. Extend the existing `ItemSlot` and `BankSlot` values with copied strings while preserving raw IDs, quantities, and bank tab positions; make State Inspector include null name fields in JSON.

**Tech Stack:** Java 11, RuneLite API 1.12.x, Gson, Gradle runnable assertion self-tests.

**Spec:** `docs/superpowers/specs/2026-08-21-named-entity-ids-design.md`

## Global Constraints

- Cover active non-minigame read APIs only; do not modify Guardians of the Rift or other minigame APIs.
- Pair public item IDs with item names now; apply the documented `objectId`/`objectName` and `npcId`/`npcName` convention only when scoped domains emit those IDs.
- Keep structural IDs—including region, world-view, sprite, slot, and tab IDs—numeric-only.
- Keep raw IDs and quantities unchanged and authoritative.
- Use `null` for empty or unresolved entity names and preserve that field in State Inspector JSON.
- Resolve definitions only during client-thread snapshot capture; never retain mutable RuneLite compositions.
- Preserve unrelated workspace changes and the untracked `outputs/` directory.

---

### Task 1: Named and indexed Inventory/Equipment slots

**Files:**
- Modify: `src/main/java/net/runelite/client/plugins/m8aq/api/items/ItemSlot.java`
- Modify: `src/main/java/net/runelite/client/plugins/m8aq/api/items/Inventory.java`
- Modify: `src/main/java/net/runelite/client/plugins/m8aq/api/items/Equipment.java`
- Test: `src/test/java/net/runelite/client/plugins/m8aq/api/items/InventorySelfTest.java`
- Test: `src/test/java/net/runelite/client/plugins/m8aq/api/items/EquipmentSelfTest.java`

**Interfaces:**
- Consumes: `Client.getItemDefinition(int)` and `ItemComposition.getName()`.
- Produces: `new ItemSlot(int slot, int itemId, String itemName, int quantity)`, `ItemSlot.getSlot()`, and `ItemSlot.getItemName()`.

- [ ] **Step 1: Write failing Inventory assertions and definition proxy**

Add occupied-slot assertions:

```java
assert state.getItem(0).getSlot() == 0;
assert state.getItem(0).getItemName().equals("Item 100");
assert state.getItem(5).getSlot() == 5;
assert state.getItem(5).getItemName().equals("Item 100");

Inventory.State empty = Inventory.getState(client(container(
	new Item[]{new Item(-1, 0)}, 0)));
assert empty.getItem(0).getSlot() == 0;
assert empty.getItem(0).getItemName() == null;

Inventory.State unresolved = Inventory.getState(client(container(
	new Item[]{new Item(999, 1)}, 1)));
assert unresolved.getItem(0).getItemId() == 999;
assert unresolved.getItem(0).getItemName() == null;
```

Teach the fake client to return an item composition:

```java
case "getItemContainer":
	return (int) args[0] == InventoryID.INV ? inventory : null;
case "getItemDefinition":
	int itemId = (int) args[0];
	return itemId == 999 ? null : itemDefinition(itemId);
```

Add the proxy helper and update the unmodifiable-list mutation to the new approved constructor:

```java
private static ItemComposition itemDefinition(int itemId)
{
	return proxy(ItemComposition.class, (method, args) ->
		"getName".equals(method) ? "Item " + itemId : null);
}

slots.add(new ItemSlot(0, 1, "Item 1", 1));
```

- [ ] **Step 2: Write failing Equipment assertions and definition proxy**

Add these assertions:

```java
assert state.getWeapon().getSlot() == EquipmentInventorySlot.WEAPON.getSlotIdx();
assert state.getWeapon().getItemName().equals("Item 100");
assert state.getShield().getSlot() == EquipmentInventorySlot.SHIELD.getSlotIdx();
assert state.getShield().getItemName().equals("Item 200");
assert state.getItem(EquipmentInventorySlot.HEAD).getSlot()
	== EquipmentInventorySlot.HEAD.getSlotIdx();
assert state.getItem(EquipmentInventorySlot.HEAD).getItemName() == null;
```

Import `ItemComposition`, replace the Equipment fake-client lambda with the definition-aware switch, and add its proxy helper:

```java
private static Client client(ItemContainer equipment)
{
	return proxy(Client.class, (method, args) ->
	{
		switch (method)
		{
			case "getItemContainer":
				return (int) args[0] == InventoryID.WORN ? equipment : null;
			case "getItemDefinition":
				return itemDefinition((int) args[0]);
			default:
				return null;
		}
	});
}

private static ItemComposition itemDefinition(int itemId)
{
	return proxy(ItemComposition.class, (method, args) ->
		"getName".equals(method) ? "Item " + itemId : null);
}
```

Update the map mutation to:

```java
slots.put(EquipmentInventorySlot.HEAD, new ItemSlot(0, 1, "Item 1", 1));
```

- [ ] **Step 3: Run targeted tests to verify RED**

Run:

```bash
./gradlew inventorySelfTest equipmentSelfTest
```

Expected: test compilation fails because `ItemSlot` lacks the approved constructor and getters.

- [ ] **Step 4: Extend `ItemSlot` with immutable slot/name fields**

Replace its fields and constructor with:

```java
/** @return zero-based container slot */
@Getter
private final int slot;
/** @return raw item ID, or {@code -1} for an empty slot */
@Getter
private final int itemId;
/** @return canonical item name, or {@code null} for an empty/unresolved ID */
@Getter
private final String itemName;
/** @return raw item quantity */
@Getter
private final int quantity;

public ItemSlot(int slot, int itemId, String itemName, int quantity)
{
	this.slot = slot;
	this.itemId = itemId;
	this.itemName = itemName;
	this.quantity = quantity;
}
```

Update `toString()` so it includes all four fields.

- [ ] **Step 5: Capture indexed, named Inventory slots**

Import `ItemComposition`, iterate by index, and add:

```java
Item[] items = container.getItems();
for (int slot = 0; slot < items.length; slot++)
{
	Item item = items[slot];
	slots.add(new ItemSlot(slot, item.getId(), itemName(client, item.getId()), item.getQuantity()));
}
```

Add the local resolver:

```java
private static String itemName(Client client, int itemId)
{
	if (itemId < 0)
	{
		return null;
	}
	ItemComposition item = client.getItemDefinition(itemId);
	return item == null ? null : item.getName();
}
```

- [ ] **Step 6: Capture indexed, named Equipment slots**

For every `EquipmentInventorySlot`, create the value with its RuneLite slot index:

```java
slots.put(slot, item == null
	? new ItemSlot(index, -1, null, 0)
	: new ItemSlot(index, item.getId(), itemName(client, item.getId()), item.getQuantity()));
```

Add the sentinel-safe Equipment resolver:

```java
private static String itemName(Client client, int itemId)
{
	if (itemId < 0)
	{
		return null;
	}
	ItemComposition item = client.getItemDefinition(itemId);
	return item == null ? null : item.getName();
}
```

- [ ] **Step 7: Run targeted tests to verify GREEN**

Run:

```bash
./gradlew inventorySelfTest equipmentSelfTest
```

Expected: both tasks report `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit the item-container change**

```bash
git add src/main/java/net/runelite/client/plugins/m8aq/api/items/ItemSlot.java \
  src/main/java/net/runelite/client/plugins/m8aq/api/items/Inventory.java \
  src/main/java/net/runelite/client/plugins/m8aq/api/items/Equipment.java \
  src/test/java/net/runelite/client/plugins/m8aq/api/items/InventorySelfTest.java \
  src/test/java/net/runelite/client/plugins/m8aq/api/items/EquipmentSelfTest.java
git commit -m "Add names and positions to item slots"
```

### Task 2: Named Bank slots

**Files:**
- Modify: `src/main/java/net/runelite/client/plugins/m8aq/api/storage/Bank.java`
- Test: `src/test/java/net/runelite/client/plugins/m8aq/api/storage/BankSelfTest.java`

**Interfaces:**
- Consumes: `Client.getItemDefinition(int)` and `ItemComposition.getName()`.
- Produces: `Bank.BankSlot.getItemName()` while preserving `getSlot()`, `getTabNumber()`, and `getPositionInTab()`.

- [ ] **Step 1: Add failing name assertions to `BankSelfTest`**

Import `java.util.Objects`, extend `assertSlot` with `String itemName`, then assert it:

```java
assert Objects.equals(slot.getItemName(), itemName);
```

Replace the current calls with named expectations:

```java
assertSlot(state.getItem(0), 100, "Item 100", 4, 0, 0, 0);
assertSlot(state.getItem(4), -1, null, 0, 4, 0, 4);
assertSlot(state.getItem(5), 200, "Item 200", 2, 5, 1, 0);
assertSlot(state.getItem(7), 300, "Item 300", 1, 7, 2, 0);
assertSlot(state.getItem(9), 302, "Item 302", 1, 9, 2, 2);
assertSlot(large.getItem(688), 400, "Item 400", 1, 688, 0, 688);
assertSlot(large.getItem(689), 400, "Item 400", 1, 689, 1, 0);
assertSlot(large.getItem(700), 400, "Item 400", 1, 700, 2, 0);
assertSlot(large.getItem(721), 400, "Item 400", 1, 721, 2, 21);
```

Import `ItemComposition`, add this branch to the existing bank fake-client switch:

```java
case "getItemDefinition":
	return itemDefinition((int) args[0]);
```

Add the bank test helper:

```java
private static ItemComposition itemDefinition(int itemId)
{
	return proxy(ItemComposition.class, (method, args) ->
		"getName".equals(method) ? "Item " + itemId : null);
}
```

- [ ] **Step 2: Run the Bank test to verify RED**

Run:

```bash
./gradlew bankSelfTest
```

Expected: test compilation fails because `BankSlot.getItemName()` does not exist.

- [ ] **Step 3: Add item names to `BankSlot` and snapshot capture**

Add the documented field:

```java
/** @return canonical item name, or {@code null} for an empty/unresolved ID */
@Getter
private final String itemName;
```

Update the constructor signature to:

```java
private BankSlot(
	int itemId,
	String itemName,
	int quantity,
	int slot,
	int tabNumber,
	int positionInTab)
```

Create each slot with:

```java
slots.add(new BankSlot(
	item.getId(),
	itemName(client, item.getId()),
	item.getQuantity(),
	slot,
	tabNumber,
	positionInTab));
```

Import `ItemComposition` and add the Bank resolver:

```java
private static String itemName(Client client, int itemId)
{
	if (itemId < 0)
	{
		return null;
	}
	ItemComposition item = client.getItemDefinition(itemId);
	return item == null ? null : item.getName();
}
```

- [ ] **Step 4: Run the Bank test to verify GREEN**

Run:

```bash
./gradlew bankSelfTest
```

Expected: `BUILD SUCCESSFUL`, including the existing 722-entry tab-order regression.

- [ ] **Step 5: Commit the Bank change**

```bash
git add src/main/java/net/runelite/client/plugins/m8aq/api/storage/Bank.java \
  src/test/java/net/runelite/client/plugins/m8aq/api/storage/BankSelfTest.java
git commit -m "Add names to bank slots"
```

### Task 3: Preserve null names in State Inspector JSON

**Files:**
- Modify: `src/main/java/net/runelite/client/plugins/m8aq/stateinspector/StateReader.java`
- Test: `src/test/java/net/runelite/client/plugins/m8aq/stateinspector/StateReaderSelfTest.java`

**Interfaces:**
- Consumes: nested immutable snapshot values with nullable sibling name fields.
- Produces: JSON strings that retain explicit null properties.

- [ ] **Step 1: Add a failing null-field JSON assertion**

Extend `FakeValue` with a null name:

```java
private final int value;
private final String name;

private FakeValue(int value)
{
	this.value = value;
	this.name = null;
}
```

Change the expected nested JSON to:

```java
assert values.get("getNested").equals("{\"entry\":{\"value\":7,\"name\":null}}");
```

- [ ] **Step 2: Run the State Reader test to verify RED**

Run:

```bash
./gradlew stateReaderSelfTest
```

Expected: assertion failure because default Gson omits `name`.

- [ ] **Step 3: Enable explicit null serialization**

Replace the Gson construction with:

```java
private static final Gson GSON = new GsonBuilder().serializeNulls().create();
```

Replace the `Gson` import with `com.google.gson.GsonBuilder` plus `com.google.gson.Gson`.

- [ ] **Step 4: Run the State Reader test to verify GREEN**

Run:

```bash
./gradlew stateReaderSelfTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run complete verification and rebuild the development JAR**

Run:

```bash
./gradlew clean check
git diff --check
./gradlew jar
```

Expected: all 15 Gradle check tasks pass, diff validation prints nothing, and the JAR build succeeds. Confirm the existing development-client log reports `Reloaded 1 plugin(s)` or the current active plugin count without starting regular RuneLite or using computer control.

- [ ] **Step 6: Commit the inspector change**

```bash
git add src/main/java/net/runelite/client/plugins/m8aq/stateinspector/StateReader.java \
  src/test/java/net/runelite/client/plugins/m8aq/stateinspector/StateReaderSelfTest.java
git commit -m "Preserve null names in state output"
```

- [ ] **Step 7: Update the local research log**

Record targeted RED/GREEN results, full-suite results, diff validation, JAR build, and hot-reload evidence in ignored `research-named-entity-ids.md`. Confirm `git status --short` lists no task files and still leaves unrelated `outputs/` untouched.
