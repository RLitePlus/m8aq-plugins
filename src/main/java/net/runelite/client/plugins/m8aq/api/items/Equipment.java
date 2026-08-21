package net.runelite.client.plugins.m8aq.api.items;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;

/** Provides a read-only snapshot of the local player's equipped items. */
public final class Equipment
{
	private Equipment()
	{
	}

	/**
	 * Reads the current equipment state from the client.
	 * This method must be called on the client thread.
	 *
	 * @param client RuneLite client
	 * @return immutable equipment snapshot
	 */
	public static State getState(Client client)
	{
		Objects.requireNonNull(client, "client");
		ItemContainer container = client.getItemContainer(InventoryID.WORN);
		if (container == null)
		{
			return new State(false, Collections.emptyMap());
		}

		Item[] items = container.getItems();
		Map<EquipmentInventorySlot, ItemSlot> slots = new EnumMap<>(EquipmentInventorySlot.class);
		for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
		{
			int index = slot.getSlotIdx();
			Item item = index < items.length ? items[index] : null;
			slots.put(slot, item == null
				? new ItemSlot(-1, 0)
				: new ItemSlot(item.getId(), item.getQuantity()));
		}
		return new State(true, Collections.unmodifiableMap(slots));
	}

	/** Immutable equipment state captured from one client read. */
	public static final class State
	{
		/** @return whether the equipment container was available */
		@Getter
		private final boolean available;
		/** @return immutable item value for every semantic equipment slot */
		@Getter
		private final Map<EquipmentInventorySlot, ItemSlot> slots;

		private State(boolean available, Map<EquipmentInventorySlot, ItemSlot> slots)
		{
			this.available = available;
			this.slots = slots;
		}

		/**
		 * Returns an item by semantic equipment slot.
		 *
		 * @param slot equipment slot
		 * @return immutable slot, or {@code null} when unavailable or given null
		 */
		public ItemSlot getItem(EquipmentInventorySlot slot)
		{
			return slot == null ? null : slots.get(slot);
		}

		/**
		 * Checks whether an exact raw item ID is equipped.
		 *
		 * @param itemId raw item ID
		 * @return whether any equipment slot contains the ID
		 */
		public boolean isEquipped(int itemId)
		{
			for (ItemSlot slot : slots.values())
			{
				if (slot.getItemId() == itemId)
				{
					return true;
				}
			}
			return false;
		}

		/** @return weapon slot, or {@code null} when unavailable */
		public ItemSlot getWeapon()
		{
			return getItem(EquipmentInventorySlot.WEAPON);
		}

		/** @return shield/off-hand slot, or {@code null} when unavailable */
		public ItemSlot getShield()
		{
			return getItem(EquipmentInventorySlot.SHIELD);
		}
	}
}
