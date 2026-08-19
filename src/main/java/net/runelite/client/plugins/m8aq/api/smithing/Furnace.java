package net.runelite.client.plugins.m8aq.api.smithing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;

/** Provides a read-only snapshot of ordinary bar smelting and furnace activity. */
public final class Furnace
{
	private static final String SMELTING_TITLE = "What would you like to smelt?";

	private Furnace()
	{
	}

	/**
	 * Reads the current furnace state from the client.
	 * This method must be called on the client thread.
	 *
	 * @param client RuneLite client
	 * @return immutable state snapshot
	 */
	public static State getState(Client client)
	{
		Objects.requireNonNull(client, "client");
		boolean operatingFurnace = isOperatingFurnace(client.getLocalPlayer());
		Widget root = client.getWidget(InterfaceID.Skillmulti.UNIVERSE);
		Widget title = client.getWidget(InterfaceID.Skillmulti.TITLE);
		boolean open = root != null && !root.isHidden()
			&& title != null && !title.isHidden()
			&& SMELTING_TITLE.equals(title.getText());
		if (!open)
		{
			return new State(false, operatingFurnace, 0, Collections.emptyList());
		}

		return new State(
			true,
			operatingFurnace,
			client.getVarcIntValue(VarClientID.SKILLMULTI_QUANTITY),
			readProducts(client));
	}

	private static boolean isOperatingFurnace(Player player)
	{
		if (player == null)
		{
			return false;
		}

		int animation = player.getAnimation();
		return animation == AnimationID.HUMAN_FURNACE
			|| animation == AnimationID.HUMAN_FURNACE_NOSTALL;
	}

	private static List<Product> readProducts(Client client)
	{
		List<Product> products = new ArrayList<>();
		for (int componentId = InterfaceID.Skillmulti.A;
			componentId <= InterfaceID.Skillmulti.R;
			componentId++)
		{
			Widget item = findItem(client.getWidget(componentId));
			if (item == null)
			{
				continue;
			}

			int itemId = item.getItemId();
			ItemComposition definition = client.getItemDefinition(itemId);
			products.add(new Product(
				itemId,
				definition == null ? "Unknown" : definition.getName()));
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

	/** Immutable product displayed by the bar-smelting interface. */
	public static final class Product
	{
		private final int itemId;
		private final String name;

		private Product(int itemId, String name)
		{
			this.itemId = itemId;
			this.name = name;
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

		@Override
		public String toString()
		{
			return name + " [id=" + itemId + ']';
		}
	}

	/** Immutable furnace state captured from one client read. */
	public static final class State
	{
		private final boolean open;
		private final boolean operatingFurnace;
		private final int requestedQuantity;
		private final List<Product> displayedProducts;

		private State(
			boolean open,
			boolean operatingFurnace,
			int requestedQuantity,
			List<Product> displayedProducts)
		{
			this.open = open;
			this.operatingFurnace = operatingFurnace;
			this.requestedQuantity = requestedQuantity;
			this.displayedProducts = displayedProducts;
		}

		/** @return whether the ordinary bar-smelting selection screen is visible */
		public boolean isOpen()
		{
			return open;
		}

		/** @return whether the local player is performing a furnace animation */
		public boolean isOperatingFurnace()
		{
			return operatingFurnace;
		}

		/** @return requested smelting quantity, or zero while the screen is closed */
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
