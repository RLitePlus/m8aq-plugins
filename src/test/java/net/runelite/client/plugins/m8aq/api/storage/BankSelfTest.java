package net.runelite.client.plugins.m8aq.api.storage;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.m8aq.api.items.ItemSlot;

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

		Bank.State state = Bank.getState(client(container(items), widgets, varbits));
		assert state.isAvailable();
		assert state.isOpen();
		assert state.getSlots().size() == 10;
		assert state.getItem(1).getItemId() == 101;
		assert state.getItem(1).getQuantity() == 0;
		assert state.getItem(4).getItemId() == -1;
		assert state.getItem(-1) == null;
		assert state.getItem(10) == null;
		assert state.count(100) == 10;
		assert state.getCapacity() == 1410;
		assert state.getTabs().size() == 2;
		assertTab(state.getTabs().get(0), 1, 0, 2);
		assertTab(state.getTabs().get(1), 2, 2, 3);
		assertUnmodifiable(state.getSlots(), state.getTabs());

		widgets.put(InterfaceID.Bankmain.UNIVERSE, widget(true, ""));
		Bank.State closed = Bank.getState(client(container(items), widgets, varbits));
		assert closed.isAvailable();
		assert !closed.isOpen();
		assert closed.getCapacity() == -1;
		assert closed.getTabs().size() == 2;

		Bank.State unavailable = Bank.getState(client(null, widgets, varbits));
		assert !unavailable.isAvailable();
		assert !unavailable.isOpen();
		assert unavailable.getSlots().isEmpty();
		assert unavailable.getCapacity() == -1;
		assert unavailable.getTabs().isEmpty();
	}

	private static void assertTab(Bank.BankTab tab, int number, int startSlot, int itemCount)
	{
		assert tab.getNumber() == number;
		assert tab.getStartSlot() == startSlot;
		assert tab.getItemCount() == itemCount;
	}

	private static void assertUnmodifiable(List<ItemSlot> slots, List<Bank.BankTab> tabs)
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
