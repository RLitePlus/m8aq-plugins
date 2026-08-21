package net.runelite.client.plugins.m8aq.api.items;

import lombok.Getter;

/** Immutable raw item ID and quantity copied from one container slot. */
public final class ItemSlot
{
	/** @return raw item ID, or {@code -1} for an empty slot */
	@Getter
	private final int itemId;
	/** @return raw item quantity */
	@Getter
	private final int quantity;

	/**
	 * Creates an immutable slot value.
	 *
	 * @param itemId raw item ID
	 * @param quantity raw item quantity
	 */
	public ItemSlot(int itemId, int quantity)
	{
		this.itemId = itemId;
		this.quantity = quantity;
	}

	@Override
	public String toString()
	{
		return "ItemSlot [itemId=" + itemId + ", quantity=" + quantity + ']';
	}
}
