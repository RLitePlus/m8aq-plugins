package net.runelite.client.plugins.m8aq.api.storage;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/** Minimal runnable checks for {@link Bank}. */
public final class BankSelfTest
{
	private BankSelfTest()
	{
	}

	/** Runs the checks with Java assertions enabled. */
	public static void main(String[] args)
	{
		Item[] items = {
			new Item(100, 4),
			new Item(101, 0),
			new Item(100, 6),
			new Item(102, 1),
			new Item(-1, 0),
			new Item(200, 2),
			new Item(201, 1),
			new Item(300, 1),
			new Item(301, 1),
			new Item(302, 1)
		};
		Map<Integer, Widget> widgets = new HashMap<>();
		widgets.put(InterfaceID.Bankmain.UNIVERSE, widget(false, ""));
		widgets.put(InterfaceID.Bankmain.CAPACITY, widget(false, "1,410"));
		Map<Integer, Integer> varbits = new HashMap<>();
		varbits.put(VarbitID.BANK_TAB_1, 2);
		varbits.put(VarbitID.BANK_TAB_2, 3);
		varbits.put(VarbitID.BANK_CURRENTTAB, 0);

		Bank.State state = Bank.getState(client(container(items), widgets, varbits));
		assert state.isAvailable();
		assert state.isOpen();
		assert state.getSlots().size() == 10;
		assert state.getItem(1).getItemId() == 101;
		assert state.getItem(1).getQuantity() == 0;
		assertSlot(state.getItem(0), 100, "Item 100", 4, 0, 1, 0);
		assertSlot(state.getItem(2), 100, "Item 100", 6, 2, 2, 0);
		assertSlot(state.getItem(4), -1, null, 0, 4, 2, 2);
		assertSlot(state.getItem(5), 200, "Item 200", 2, 5, 0, 0);
		assertSlot(state.getItem(9), 302, "Item 302", 1, 9, 0, 4);
		assert state.getItem(4).getItemId() == -1;
		assert state.getItem(-1) == null;
		assert state.getItem(10) == null;
		assert state.count(100) == 10;
		assert state.getCapacity() == 1410;
		assert state.getSelectedTab() == 0;
		assert state.getTabs().size() == 2;
		assertTab(state.getTabs().get(0), 1, 0, 2);
		assertTab(state.getTabs().get(1), 2, 2, 3);
		assertUnmodifiable(state.getSlots(), state.getTabs());

		varbits.put(VarbitID.BANK_CURRENTTAB, 1);
		Bank.State firstTab = Bank.getState(client(container(items), widgets, varbits));
		assert firstTab.getSelectedTab() == 1;

		widgets.put(InterfaceID.Bankmain.UNIVERSE, widget(true, ""));
		Bank.State closed = Bank.getState(client(container(items), widgets, varbits));
		assert closed.isAvailable();
		assert !closed.isOpen();
		assert closed.getCapacity() == -1;
		assert closed.getSelectedTab() == -1;
		assert closed.getTabs().size() == 2;

		Item[] largeBank = new Item[722];
		Arrays.fill(largeBank, new Item(400, 1));
		varbits.put(VarbitID.BANK_TAB_1, 11);
		varbits.put(VarbitID.BANK_TAB_2, 22);
		Bank.State large = Bank.getState(client(container(largeBank), widgets, varbits));
		assertSlot(large.getItem(0), 400, "Item 400", 1, 0, 1, 0);
		assertSlot(large.getItem(10), 400, "Item 400", 1, 10, 1, 10);
		assertSlot(large.getItem(11), 400, "Item 400", 1, 11, 2, 0);
		assertSlot(large.getItem(32), 400, "Item 400", 1, 32, 2, 21);
		assertSlot(large.getItem(33), 400, "Item 400", 1, 33, 0, 0);
		assertSlot(large.getItem(721), 400, "Item 400", 1, 721, 0, 688);

		varbits.put(VarbitID.BANK_TAB_9, 20);
		Bank.State inconsistent = Bank.getState(client(container(items), widgets, varbits));
		assert inconsistent.getTabs().isEmpty();
		for (Bank.BankSlot slot : inconsistent.getSlots())
		{
			assert slot.getTabNumber() == -1;
			assert slot.getPositionInTab() == -1;
		}

		Bank.State unavailable = Bank.getState(client(null, widgets, varbits));
		assert !unavailable.isAvailable();
		assert !unavailable.isOpen();
		assert unavailable.getSlots().isEmpty();
		assert unavailable.getCapacity() == -1;
		assert unavailable.getSelectedTab() == -1;
		assert unavailable.getTabs().isEmpty();
	}

	private static void assertSlot(
		Bank.BankSlot slot,
		int itemId,
		String itemName,
		int quantity,
		int absoluteSlot,
		int tabNumber,
		int positionInTab)
	{
		assert slot.getItemId() == itemId;
		assert Objects.equals(slot.getItemName(), itemName);
		assert slot.getQuantity() == quantity;
		assert slot.getSlot() == absoluteSlot;
		assert slot.getTabNumber() == tabNumber;
		assert slot.getPositionInTab() == positionInTab;
	}

	private static void assertTab(Bank.BankTab tab, int number, int startSlot, int itemCount)
	{
		assert tab.getNumber() == number;
		assert tab.getStartSlot() == startSlot;
		assert tab.getItemCount() == itemCount;
	}

	private static void assertUnmodifiable(List<Bank.BankSlot> slots, List<Bank.BankTab> tabs)
	{
		try
		{
			slots.clear();
			assert false;
		}
		catch (UnsupportedOperationException expected)
		{
			// Expected.
		}
		try
		{
			tabs.clear();
			assert false;
		}
		catch (UnsupportedOperationException expected)
		{
			// Expected.
		}
	}

	private static Client client(
		ItemContainer bank,
		Map<Integer, Widget> widgets,
		Map<Integer, Integer> varbits)
	{
		return proxy(Client.class, (method, args) ->
		{
			switch (method)
			{
				case "getItemDefinition":
					return itemDefinition((int) args[0]);
				case "getItemContainer":
					return (int) args[0] == InventoryID.BANK ? bank : null;
				case "getWidget":
					return widgets.get((int) args[0]);
				case "getVarbitValue":
					return varbits.getOrDefault((int) args[0], 0);
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

	private static Widget widget(boolean hidden, String text)
	{
		return proxy(Widget.class, (method, args) ->
		{
			switch (method)
			{
				case "isHidden":
					return hidden;
				case "getText":
					return text;
				default:
					return null;
			}
		});
	}

	private static ItemContainer container(Item[] items)
	{
		return proxy(ItemContainer.class, (method, args) ->
		{
			switch (method)
			{
				case "getId":
					return InventoryID.BANK;
				case "getItems":
					return items.clone();
				case "getItem":
					int slot = (int) args[0];
					return slot >= 0 && slot < items.length ? items[slot] : null;
				case "contains":
					return find(items, (int) args[0]) >= 0;
				case "count":
					return args == null ? filledSlots(items) : quantity(items, (int) args[0]);
				case "size":
					return items.length;
				case "find":
					return find(items, (int) args[0]);
				default:
					return null;
			}
		});
	}

	private static int find(Item[] items, int itemId)
	{
		for (int slot = 0; slot < items.length; slot++)
		{
			if (items[slot].getId() == itemId)
			{
				return slot;
			}
		}
		return -1;
	}

	private static int quantity(Item[] items, int itemId)
	{
		int total = 0;
		for (Item item : items)
		{
			if (item.getId() == itemId)
			{
				total += item.getQuantity();
			}
		}
		return total;
	}

	private static int filledSlots(Item[] items)
	{
		int total = 0;
		for (Item item : items)
		{
			if (item.getId() >= 0)
			{
				total++;
			}
		}
		return total;
	}

	private static <T> T proxy(Class<T> type, Handler handler)
	{
		Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
			(instance, method, args) ->
			{
				Object value = handler.invoke(method.getName(), args);
				return value == null ? defaultValue(method.getReturnType()) : value;
			});
		return type.cast(proxy);
	}

	private static Object defaultValue(Class<?> type)
	{
		if (!type.isPrimitive())
		{
			return null;
		}
		if (type == boolean.class)
		{
			return false;
		}
		if (type == char.class)
		{
			return '\0';
		}
		return 0;
	}

	@FunctionalInterface
	private interface Handler
	{
		Object invoke(String method, Object[] args);
	}
}
