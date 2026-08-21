package net.runelite.client.plugins.m8aq.api.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.m8aq.api.items.ItemSlot;

/** Provides a read-only snapshot of the currently loaded bank. */
public final class Bank
{
	private static final Pattern NUMBER = Pattern.compile("[0-9][0-9,]*");
	private static final int[] TAB_COUNTS = {
		VarbitID.BANK_TAB_1,
		VarbitID.BANK_TAB_2,
		VarbitID.BANK_TAB_3,
		VarbitID.BANK_TAB_4,
		VarbitID.BANK_TAB_5,
		VarbitID.BANK_TAB_6,
		VarbitID.BANK_TAB_7,
		VarbitID.BANK_TAB_8,
		VarbitID.BANK_TAB_9
	};

	private Bank()
	{
	}

	/**
	 * Reads the current bank state from the client.
	 * This method must be called on the client thread.
	 *
	 * @param client RuneLite client
	 * @return immutable bank snapshot
	 */
	public static State getState(Client client)
	{
		Objects.requireNonNull(client, "client");
		Widget root = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		boolean open = root != null && !root.isHidden();
		ItemContainer container = client.getItemContainer(InventoryID.BANK);
		if (container == null)
		{
			return new State(false, open, Collections.emptyList(), -1, Collections.emptyList());
		}

		List<ItemSlot> slots = new ArrayList<>();
		for (Item item : container.getItems())
		{
			slots.add(new ItemSlot(item.getId(), item.getQuantity()));
		}
		List<ItemSlot> immutableSlots = Collections.unmodifiableList(slots);
		return new State(
			true,
			open,
			immutableSlots,
			open ? readCapacity(client.getWidget(InterfaceID.Bankmain.CAPACITY)) : -1,
			readTabs(client, slots.size()));
	}

	private static int readCapacity(Widget widget)
	{
		if (widget == null || widget.isHidden() || widget.getText() == null)
		{
			return -1;
		}

		Matcher matcher = NUMBER.matcher(widget.getText());
		String value = null;
		while (matcher.find())
		{
			value = matcher.group();
		}
		if (value == null)
		{
			return -1;
		}
		try
		{
			return Integer.parseInt(value.replace(",", ""));
		}
		catch (NumberFormatException ex)
		{
			return -1;
		}
	}

	private static List<BankTab> readTabs(Client client, int slotCount)
	{
		int[] counts = new int[TAB_COUNTS.length];
		int tabItems = 0;
		for (int index = 0; index < TAB_COUNTS.length; index++)
		{
			counts[index] = Math.max(0, client.getVarbitValue(TAB_COUNTS[index]));
			tabItems += counts[index];
		}
		if (tabItems > slotCount)
		{
			return Collections.emptyList();
		}

		int startSlot = 0;
		List<BankTab> tabs = new ArrayList<>();
		for (int index = 0; index < counts.length; index++)
		{
			if (counts[index] > 0)
			{
				tabs.add(new BankTab(index + 1, startSlot, counts[index]));
				startSlot += counts[index];
			}
		}
		return Collections.unmodifiableList(tabs);
	}

	/** Immutable visible bank-tab boundary. */
	public static final class BankTab
	{
		/** @return one-based bank-tab number */
		@Getter
		private final int number;
		/** @return zero-based starting slot in bank-container order */
		@Getter
		private final int startSlot;
		/** @return number of entries in the tab */
		@Getter
		private final int itemCount;

		private BankTab(int number, int startSlot, int itemCount)
		{
			this.number = number;
			this.startSlot = startSlot;
			this.itemCount = itemCount;
		}
	}

	/** Immutable bank state captured from one client read. */
	public static final class State
	{
		/** @return whether authoritative bank-container state was available */
		@Getter
		private final boolean available;
		/** @return whether the bank interface was visibly open */
		@Getter
		private final boolean open;
		/** @return immutable positional bank entries */
		@Getter
		private final List<ItemSlot> slots;
		/** @return displayed bank capacity, or {@code -1} when unavailable */
		@Getter
		private final int capacity;
		/** @return immutable visible bank-tab boundaries in order */
		@Getter
		private final List<BankTab> tabs;

		private State(
			boolean available,
			boolean open,
			List<ItemSlot> slots,
			int capacity,
			List<BankTab> tabs)
		{
			this.available = available;
			this.open = open;
			this.slots = slots;
			this.capacity = capacity;
			this.tabs = tabs;
		}

		/**
		 * Returns a positional bank entry.
		 *
		 * @param slot zero-based slot index
		 * @return immutable slot, or {@code null} when out of bounds
		 */
		public ItemSlot getItem(int slot)
		{
			return slot >= 0 && slot < slots.size() ? slots.get(slot) : null;
		}

		/**
		 * Counts an exact raw item ID in loaded bank state.
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
	}
}
