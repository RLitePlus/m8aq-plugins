package net.runelite.client.plugins.m8aq.api.smithing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;

/** Provides a read-only snapshot of the standard anvil interface and activity. */
public final class Anvil
{
	private static final int REQUIRED_BARS_ENUM = 845;
	private static final int REQUIRED_LEVEL_ENUM = 846;

	private Anvil()
	{
	}

	/**
	 * Reads the current standard-anvil state from the client.
	 * This method must be called on the client thread.
	 *
	 * @param client RuneLite client
	 * @return immutable state snapshot
	 */
	public static State getState(Client client)
	{
		Objects.requireNonNull(client, "client");
		boolean smithing = isSmithing(client.getLocalPlayer());
		Widget root = client.getWidget(InterfaceID.Smithing.UNIVERSE);
		if (root == null || root.isHidden())
		{
			return new State(false, smithing, Bar.UNKNOWN, 0, 0, Collections.emptyList());
		}

		Bar selectedBar = Bar.fromValue(client.getVarbitValue(VarbitID.SMITHING_BAR_TYPE));
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		int selectedBarCount = inventory == null || selectedBar == Bar.UNKNOWN
			? 0
			: inventory.count(selectedBar.itemId);

		return new State(
			true,
			smithing,
			selectedBar,
			selectedBarCount,
			client.getVarpValue(VarPlayerID.MAKEXCRAFTING),
			readProducts(client));
	}

	private static boolean isSmithing(Player player)
	{
		if (player == null)
		{
			return false;
		}

		switch (player.getAnimation())
		{
			case AnimationID.HUMAN_SMITHING:
			case AnimationID.HUMAN_SMITHING_NOREPLACE:
			case AnimationID.HUMAN_SMITHING_IMCANDO_HAMMER:
				return true;
			default:
				return false;
		}
	}

	private static List<Product> readProducts(Client client)
	{
		EnumComposition requiredBars = client.getEnum(REQUIRED_BARS_ENUM);
		EnumComposition requiredLevels = client.getEnum(REQUIRED_LEVEL_ENUM);
		List<Product> products = new ArrayList<>();
		for (int componentId = InterfaceID.Smithing.DAGGER;
			componentId <= InterfaceID.Smithing.OTHER_6;
			componentId++)
		{
			Widget item = findItem(client.getWidget(componentId));
			if (item == null)
			{
				continue;
			}

			int itemId = item.getItemId();
			int level = enumValue(requiredLevels, itemId, -1);
			products.add(new Product(
				itemId,
				itemName(client, itemId),
				item.getItemQuantity(),
				enumValue(requiredBars, itemId, -1),
				level < 0 || level == Integer.MAX_VALUE ? -1 : Math.max(1, level)));
		}
		return Collections.unmodifiableList(products);
	}

	private static Widget findItem(Widget slot)
	{
		if (slot == null || slot.isHidden())
		{
			return null;
		}

		Widget[] children = slot.getDynamicChildren();
		if (children != null)
		{
			for (Widget child : children)
			{
				if (child != null && !child.isHidden() && child.getItemId() > 0)
				{
					return child;
				}
			}
		}
		return null;
	}

	private static int enumValue(EnumComposition values, int key, int fallback)
	{
		return values == null ? fallback : values.getIntValue(key);
	}

	private static String itemName(Client client, int itemId)
	{
		ItemComposition item = client.getItemDefinition(itemId);
		return item == null ? "Unknown" : item.getName();
	}

	/** Metal whose standard-anvil product table is displayed. */
	public enum Bar
	{
		UNKNOWN(-1),
		BRONZE(ItemID.BRONZE_BAR),
		IRON(ItemID.IRON_BAR),
		STEEL(ItemID.STEEL_BAR),
		MITHRIL(ItemID.MITHRIL_BAR),
		ADAMANTITE(ItemID.ADAMANTITE_BAR),
		RUNITE(ItemID.RUNITE_BAR),
		LOVAKITE(ItemID.LOVAKITE_BAR);

		private final int itemId;

		Bar(int itemId)
		{
			this.itemId = itemId;
		}

		private static Bar fromValue(int value)
		{
			return value >= 1 && value < values().length ? values()[value] : UNKNOWN;
		}
	}

	/** Immutable standard-anvil product displayed by the client. */
	public static final class Product
	{
		private final int itemId;
		private final String name;
		private final int outputQuantity;
		private final int requiredBars;
		private final int requiredLevel;

		private Product(
			int itemId,
			String name,
			int outputQuantity,
			int requiredBars,
			int requiredLevel)
		{
			this.itemId = itemId;
			this.name = name;
			this.outputQuantity = outputQuantity;
			this.requiredBars = requiredBars;
			this.requiredLevel = requiredLevel;
		}

		/** @return output item ID */
		public int getItemId()
		{
			return itemId;
		}

		/** @return output item name */
		public String getName()
		{
			return name;
		}

		/** @return items produced per smithing action */
		public int getOutputQuantity()
		{
			return outputQuantity;
		}

		/** @return bars required per smithing action, or {@code -1} if unavailable */
		public int getRequiredBars()
		{
			return requiredBars;
		}

		/** @return required Smithing level, or {@code -1} if unavailable */
		public int getRequiredLevel()
		{
			return requiredLevel;
		}

		@Override
		public String toString()
		{
			return name + " [id=" + itemId + ", output=" + outputQuantity
				+ ", bars=" + requiredBars + ", level=" + requiredLevel + ']';
		}
	}

	/** Immutable standard-anvil state captured from one client read. */
	public static final class State
	{
		private final boolean open;
		private final boolean smithing;
		private final Bar selectedBar;
		private final int selectedBarCount;
		private final int requestedQuantity;
		private final List<Product> displayedProducts;

		private State(
			boolean open,
			boolean smithing,
			Bar selectedBar,
			int selectedBarCount,
			int requestedQuantity,
			List<Product> displayedProducts)
		{
			this.open = open;
			this.smithing = smithing;
			this.selectedBar = selectedBar;
			this.selectedBarCount = selectedBarCount;
			this.requestedQuantity = requestedQuantity;
			this.displayedProducts = displayedProducts;
		}

		/** @return whether the standard anvil interface is visible */
		public boolean isOpen()
		{
			return open;
		}

		/** @return whether the local player is performing a standard smithing animation */
		public boolean isSmithing()
		{
			return smithing;
		}

		/** @return displayed bar, or {@link Bar#UNKNOWN} while closed or unrecognised */
		public Bar getSelectedBar()
		{
			return selectedBar;
		}

		/** @return selected bars in inventory, or zero while closed */
		public int getSelectedBarCount()
		{
			return selectedBarCount;
		}

		/**
		 * @return requested Make-X quantity, or zero while closed; 28 may also mean All
		 */
		public int getRequestedQuantity()
		{
			return requestedQuantity;
		}

		/** @return immutable list of products currently displayed */
		public List<Product> getDisplayedProducts()
		{
			return displayedProducts;
		}
	}
}
