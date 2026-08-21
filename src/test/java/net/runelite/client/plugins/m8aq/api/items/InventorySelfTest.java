package net.runelite.client.plugins.m8aq.api.items;

import java.lang.reflect.Proxy;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;

/** Minimal runnable checks for {@link Inventory}. */
public final class InventorySelfTest
{
	private InventorySelfTest()
	{
	}

	/** Runs the checks with Java assertions enabled. */
	public static void main(String[] args)
	{
		Item[] items = new Item[28];
		for (int slot = 0; slot < items.length; slot++)
		{
			items[slot] = new Item(0, 0);
		}
		items[0] = new Item(100, 3);
		items[5] = new Item(100, 2);
		items[27] = new Item(200, 1);

		Inventory.State state = Inventory.getState(client(container(items, 3)));
		assert state.isAvailable();
		assert state.getSlots().size() == 28;
		assert state.getItem(0).getItemId() == 100;
		assert state.getItem(0).getQuantity() == 3;
		assert state.getItem(1).getItemId() == 0;
		assert state.getItem(-1) == null;
		assert state.getItem(28) == null;
		assert state.count(100) == 5;
		assert state.contains(200);
		assert !state.contains(300);
		assert state.getEmptySlotCount() == 25;

		items[0] = new Item(300, 99);
		assert state.getItem(0).getItemId() == 100;
		assertUnmodifiable(state.getSlots());

		Inventory.State unavailable = Inventory.getState(client(null));
		assert !unavailable.isAvailable();
		assert unavailable.getSlots().isEmpty();
		assert unavailable.getItem(0) == null;
		assert unavailable.count(100) == 0;
		assert unavailable.getEmptySlotCount() == 0;
	}

	private static void assertUnmodifiable(List<ItemSlot> slots)
	{
		try
		{
			slots.add(new ItemSlot(1, 1));
			assert false;
		}
		catch (UnsupportedOperationException expected)
		{
			// Expected.
		}
	}

	private static Client client(ItemContainer inventory)
	{
		return proxy(Client.class, (method, args) ->
			"getItemContainer".equals(method) && (int) args[0] == InventoryID.INV
				? inventory
				: null);
	}

	private static ItemContainer container(Item[] items, int filledSlots)
	{
		return proxy(ItemContainer.class, (method, args) ->
		{
			switch (method)
			{
				case "getId":
					return InventoryID.INV;
				case "getItems":
					return items.clone();
				case "getItem":
					int slot = (int) args[0];
					return slot >= 0 && slot < items.length ? items[slot] : null;
				case "contains":
					return count(items, (int) args[0]) > 0;
				case "count":
					return args == null ? filledSlots : count(items, (int) args[0]);
				case "size":
					return items.length;
				case "find":
					return find(items, (int) args[0]);
				default:
					return null;
			}
		});
	}

	private static int count(Item[] items, int itemId)
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
