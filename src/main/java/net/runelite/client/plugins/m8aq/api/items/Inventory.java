package net.runelite.client.plugins.m8aq.api.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;

/** Provides a read-only snapshot of the local player's backpack inventory. */
public final class Inventory
{
	private static final int CAPACITY = 28;

	private Inventory()
	{
	}

	/**
	 * Reads the current backpack state from the client.
	 * This method must be called on the client thread.
	 *
	 * @param client RuneLite client
	 * @return immutable inventory snapshot
	 */
	public static State getState(Client client)
	{
		Objects.requireNonNull(client, "client");
		ItemContainer container = client.getItemContainer(InventoryID.INV);
		if (container == null)
		{
			return new State(false, Collections.emptyList(), 0);
		}

		List<ItemSlot> slots = new ArrayList<>();
		for (Item item : container.getItems())
		{
			slots.add(new ItemSlot(item.getId(), item.getQuantity()));
		}
		return new State(true, Collections.unmodifiableList(slots), CAPACITY - container.count());
	}

	/** Immutable backpack state captured from one client read. */
	public static final class State
	{
		/** @return whether the backpack container was available */
		@Getter
		private final boolean available;
		/** @return immutable materialized positional slots; trailing empty slots may be omitted */
		@Getter
		private final List<ItemSlot> slots;
		/** @return number of empty slots, or zero when unavailable */
		@Getter
		private final int emptySlotCount;

		private State(boolean available, List<ItemSlot> slots, int emptySlotCount)
		{
			this.available = available;
			this.slots = slots;
			this.emptySlotCount = emptySlotCount;
		}

		/**
		 * Returns a positional slot.
		 *
		 * @param slot zero-based slot index
		 * @return immutable slot, or {@code null} when out of bounds
		 */
		public ItemSlot getItem(int slot)
		{
			return slot >= 0 && slot < slots.size() ? slots.get(slot) : null;
		}

		/**
		 * Counts an exact raw item ID.
		 *
		 * @param itemId raw item ID
		 * @return total quantity
		 */
		public int count(int itemId)
		{
			int total = 0;
			for (ItemSlot slot : slots)
			{
				if (slot.getItemId() == itemId)
				{
					total += slot.getQuantity();
				}
			}
			return total;
		}

		/**
		 * Checks for an exact raw item ID.
		 *
		 * @param itemId raw item ID
		 * @return whether any slot contains the ID
		 */
		public boolean contains(int itemId)
		{
			for (ItemSlot slot : slots)
			{
				if (slot.getItemId() == itemId)
				{
					return true;
				}
			}
			return false;
		}

	}
}
