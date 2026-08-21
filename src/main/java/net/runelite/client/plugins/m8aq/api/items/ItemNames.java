package net.runelite.client.plugins.m8aq.api.items;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;

/** Resolves canonical item names from raw item IDs. */
public final class ItemNames
{
	private ItemNames()
	{
	}

	/**
	 * Resolves an item name without looking up empty IDs.
	 *
	 * @param client RuneLite client
	 * @param itemId raw item ID
	 * @return canonical item name, or {@code null} for an empty or unresolved ID
	 */
	public static String get(Client client, int itemId)
	{
		if (itemId < 0)
		{
			return null;
		}
		ItemComposition item = client.getItemDefinition(itemId);
		return item == null ? null : item.getName();
	}
}
