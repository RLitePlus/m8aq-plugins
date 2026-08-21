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
import net.runelite.client.plugins.m8aq.api.items.ItemNames;

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
			return new State(false, open, Collections.emptyList(), -1, -1, Collections.emptyList());
		}

		Item[] items = container.getItems();
		int[] tabCounts = readTabCounts(client);
		int numberedItemCount = 0;
		for (int count : tabCounts)
		{
			numberedItemCount += count;
		}
		boolean validTabs = numberedItemCount <= items.length;
		List<BankSlot> slots = new ArrayList<>();
		for (int slot = 0; slot < items.length; slot++)
		{
			Item item = items[slot];
			int tabNumber = validTabs ? 0 : -1;
			int positionInTab = validTabs ? slot - numberedItemCount : -1;
			int tabStart = 0;
			for (int tab = 0; validTabs && tab < tabCounts.length; tab++)
			{
				int tabEnd = tabStart + tabCounts[tab];
				if (slot >= tabStart && slot < tabEnd)
				{
					tabNumber = tab + 1;
					positionInTab = slot - tabStart;
					break;
				}
				tabStart = tabEnd;
			}
			slots.add(new BankSlot(
				item.getId(), ItemNames.get(client, item.getId()), item.getQuantity(), slot, tabNumber, positionInTab));
		}
		List<BankSlot> immutableSlots = Collections.unmodifiableList(slots);
		return new State(
			true,
			open,
			immutableSlots,
			open ? readCapacity(client.getWidget(InterfaceID.Bankmain.CAPACITY)) : -1,
			open ? client.getVarbitValue(VarbitID.BANK_CURRENTTAB) : -1,
			validTabs ? readTabs(tabCounts) : Collections.emptyList());
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

	private static int[] readTabCounts(Client client)
	{
		int[] counts = new int[TAB_COUNTS.length];
		for (int index = 0; index < TAB_COUNTS.length; index++)
		{
			counts[index] = Math.max(0, client.getVarbitValue(TAB_COUNTS[index]));
		}
		return counts;
	}

	private static List<BankTab> readTabs(int[] counts)
	{
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

	/** Immutable item entry at one absolute bank-container slot. */
	public static final class BankSlot
	{
		/** @return exact raw item ID */
		@Getter
		private final int itemId;
		/** @return canonical item name, or {@code null} for an empty/unresolved ID */
		@Getter
		private final String itemName;
		/** @return raw item quantity */
		@Getter
		private final int quantity;
		/** @return zero-based absolute bank-container slot */
		@Getter
		private final int slot;
		/** @return normal bank tab number: 0 for main, 1-9 numbered, or -1 when unknown */
		@Getter
		private final int tabNumber;
		/** @return zero-based position within the normal bank tab, or -1 when unknown */
		@Getter
		private final int positionInTab;

		private BankSlot(int itemId, String itemName, int quantity, int slot, int tabNumber, int positionInTab)
		{
			this.itemId = itemId;
			this.itemName = itemName;
			this.quantity = quantity;
			this.slot = slot;
			this.tabNumber = tabNumber;
			this.positionInTab = positionInTab;
		}
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
		private final List<BankSlot> slots;
		/** @return displayed bank capacity, or {@code -1} when unavailable */
		@Getter
		private final int capacity;
		/** @return selected bank view: 0 for All, 1-9 for numbered tabs, another raw special view, or -1 when unavailable */
		@Getter
		private final int selectedTab;
		/** @return immutable visible bank-tab boundaries in order */
		@Getter
		private final List<BankTab> tabs;

		private State(
			boolean available,
			boolean open,
			List<BankSlot> slots,
			int capacity,
			int selectedTab,
			List<BankTab> tabs)
		{
			this.available = available;
			this.open = open;
			this.slots = slots;
			this.capacity = capacity;
			this.selectedTab = selectedTab;
			this.tabs = tabs;
		}

		/**
		 * Returns a positional bank entry.
		 *
		 * @param slot zero-based slot index
		 * @return immutable slot, or {@code null} when out of bounds
		 */
		public BankSlot getItem(int slot)
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
			for (BankSlot slot : slots)
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
