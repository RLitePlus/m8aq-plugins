package net.runelite.client.plugins.m8aq.api.items;

import java.lang.reflect.Proxy;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;

/** Minimal runnable checks for {@link Equipment}. */
public final class EquipmentSelfTest
{
	private EquipmentSelfTest()
	{
	}

	/** Runs the checks with Java assertions enabled. */
	public static void main(String[] args)
	{
		Item[] items = new Item[EquipmentInventorySlot.values().length];
		for (int slot = 0; slot < items.length; slot++)
		{
			items[slot] = new Item(-1, 0);
		}
		items[EquipmentInventorySlot.WEAPON.getSlotIdx()] = new Item(100, 1);
		items[EquipmentInventorySlot.SHIELD.getSlotIdx()] = new Item(200, 1);

		Equipment.State state = Equipment.getState(client(container(items)));
		assert state.isAvailable();
		assert state.getSlots().size() == EquipmentInventorySlot.values().length;
		for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
		{
			assert state.getSlots().containsKey(slot);
		}
		assert state.getWeapon().getItemId() == 100;
		assert state.getWeapon().getSlot() == EquipmentInventorySlot.WEAPON.getSlotIdx();
		assert state.getWeapon().getItemName().equals("Item 100");
		assert state.getShield().getItemId() == 200;
		assert state.getShield().getSlot() == EquipmentInventorySlot.SHIELD.getSlotIdx();
		assert state.getShield().getItemName().equals("Item 200");
		assert state.getItem(EquipmentInventorySlot.HEAD).getItemId() == -1;
		assert state.getItem(EquipmentInventorySlot.HEAD).getSlot()
			== EquipmentInventorySlot.HEAD.getSlotIdx();
		assert state.getItem(EquipmentInventorySlot.HEAD).getItemName() == null;
		assert state.getItem(null) == null;
		assert state.isEquipped(100);
		assert !state.isEquipped(300);
		assertUnmodifiable(state.getSlots());

		Equipment.State unavailable = Equipment.getState(client(null));
		assert !unavailable.isAvailable();
		assert unavailable.getSlots().isEmpty();
		assert unavailable.getWeapon() == null;
		assert !unavailable.isEquipped(100);
	}

	private static void assertUnmodifiable(Map<EquipmentInventorySlot, ItemSlot> slots)
	{
		try
		{
			slots.put(EquipmentInventorySlot.HEAD, new ItemSlot(0, 1, "Item 1", 1));
			assert false;
		}
		catch (UnsupportedOperationException expected)
		{
			// Expected.
		}
	}

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

	private static ItemContainer container(Item[] items)
	{
		return proxy(ItemContainer.class, (method, args) ->
		{
			switch (method)
			{
				case "getId":
					return InventoryID.WORN;
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
