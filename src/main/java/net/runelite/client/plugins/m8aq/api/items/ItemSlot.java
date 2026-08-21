package net.runelite.client.plugins.m8aq.api.items;

import lombok.Getter;

/** Immutable item data copied from one container slot. */
public final class ItemSlot
{
	/** @return zero-based container slot */
	@Getter
	private final int slot;
	/** @return raw item ID, or {@code -1} for an empty slot */
	@Getter
	private final int itemId;
	/** @return canonical item name, or {@code null} for an empty/unresolved ID */
	@Getter
	private final String itemName;
	/** @return raw item quantity */
	@Getter
	private final int quantity;

	/**
	 * Creates an immutable slot value.
	 *
	 * @param slot zero-based container slot
	 * @param itemId raw item ID
	 * @param itemName canonical item name, or {@code null}
	 * @param quantity raw item quantity
	 */
	public ItemSlot(int slot, int itemId, String itemName, int quantity)
	{
		this.slot = slot;
		this.itemId = itemId;
		this.itemName = itemName;
		this.quantity = quantity;
	}

	@Override
	public String toString()
	{
		return "ItemSlot [slot=" + slot + ", itemId=" + itemId + ", itemName=" + itemName
			+ ", quantity=" + quantity + ']';
	}
}
