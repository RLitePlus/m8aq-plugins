package net.runelite.client.plugins.m8aq.api.smithing;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/** Minimal runnable checks for {@link Anvil} and {@link Furnace}. */
public final class SmithingStationsSelfTest
{
	private SmithingStationsSelfTest()
	{
	}

	/** Runs the checks with Java assertions enabled. */
	public static void main(String[] args)
	{
		Map<Integer, Widget> widgets = new HashMap<>();
		Map<Integer, Integer> varbits = new HashMap<>();
		Map<Integer, Integer> varps = new HashMap<>();
		Map<Integer, Integer> varcs = new HashMap<>();
		Map<Integer, Integer> inventory = new HashMap<>();
		Map<Integer, String> itemNames = new HashMap<>();
		Map<Integer, Map<Integer, Integer>> enums = new HashMap<>();
		int[] animation = {AnimationID.HUMAN_SMITHING};

		widgets.put(InterfaceID.Smithing.UNIVERSE, widget(false, "", -1, 0));
		widgets.put(InterfaceID.Smithing.DAGGER,
			widget(false, "", -1, 0, widget(false, "", ItemID.BRONZE_DAGGER, 1)));
		varbits.put(VarbitID.SMITHING_BAR_TYPE, 1);
		varps.put(VarPlayerID.MAKEXCRAFTING, 5);
		inventory.put(ItemID.BRONZE_BAR, 12);
		itemNames.put(ItemID.BRONZE_DAGGER, "Bronze dagger");
		enums.put(845, Map.of(ItemID.BRONZE_DAGGER, 1));
		enums.put(846, Map.of(ItemID.BRONZE_DAGGER, 0));

		Client client = client(widgets, varbits, varps, varcs, inventory, itemNames, enums, animation);
		Anvil.State anvil = Anvil.getState(client);
		assert anvil.isOpen();
		assert anvil.isSmithing();
		assert anvil.getSelectedBar() == Anvil.Bar.BRONZE;
		assert anvil.getSelectedBarCount() == 12;
		assert anvil.getRequestedQuantity() == 5;
		assert anvil.getDisplayedProducts().size() == 1;
		Anvil.Product smithingProduct = anvil.getDisplayedProducts().get(0);
		assert smithingProduct.getItemId() == ItemID.BRONZE_DAGGER;
		assert smithingProduct.getOutputQuantity() == 1;
		assert smithingProduct.getRequiredBars() == 1;
		assert smithingProduct.getRequiredLevel() == 1;

		widgets.put(InterfaceID.Smithing.UNIVERSE, widget(true, "", -1, 0));
		anvil = Anvil.getState(client);
		assert !anvil.isOpen();
		assert anvil.getSelectedBar() == Anvil.Bar.UNKNOWN;
		assert anvil.getSelectedBarCount() == 0;
		assert anvil.getRequestedQuantity() == 0;
		assert anvil.getDisplayedProducts().isEmpty();

		widgets.put(InterfaceID.Skillmulti.UNIVERSE, widget(false, "", -1, 0));
		widgets.put(InterfaceID.Skillmulti.TITLE,
			widget(false, "What would you like to smelt?", -1, 0));
		widgets.put(InterfaceID.Skillmulti.A,
			widget(false, "", -1, 0, widget(false, "", ItemID.BRONZE_BAR, Integer.MAX_VALUE)));
		varcs.put(VarClientID.SKILLMULTI_QUANTITY, 7);
		itemNames.put(ItemID.BRONZE_BAR, "Bronze bar");
		animation[0] = AnimationID.HUMAN_FURNACE_NOSTALL;

		Furnace.State furnace = Furnace.getState(client);
		assert furnace.isOpen();
		assert furnace.isOperatingFurnace();
		assert furnace.getRequestedQuantity() == 7;
		assert furnace.getDisplayedProducts().size() == 1;
		assert furnace.getDisplayedProducts().get(0).getItemId() == ItemID.BRONZE_BAR;

		widgets.put(InterfaceID.Skillmulti.TITLE,
			widget(false, "What would you like to cook?", -1, 0));
		furnace = Furnace.getState(client);
		assert !furnace.isOpen();
		assert furnace.isOperatingFurnace();
		assert furnace.getRequestedQuantity() == 0;
		assert furnace.getDisplayedProducts().isEmpty();
	}

	private static Client client(
		Map<Integer, Widget> widgets,
		Map<Integer, Integer> varbits,
		Map<Integer, Integer> varps,
		Map<Integer, Integer> varcs,
		Map<Integer, Integer> inventory,
		Map<Integer, String> itemNames,
		Map<Integer, Map<Integer, Integer>> enums,
		int[] animation)
	{
		Player player = proxy(Player.class, (method, args) ->
			"getAnimation".equals(method) ? animation[0] : null);
		ItemContainer items = proxy(ItemContainer.class, (method, args) ->
			"count".equals(method) ? inventory.getOrDefault((int) args[0], 0) : null);

		return proxy(Client.class, (method, args) ->
		{
			switch (method)
			{
				case "getWidget":
					return widgets.get((int) args[0]);
				case "getVarbitValue":
					return varbits.getOrDefault((int) args[0], 0);
				case "getVarpValue":
					return varps.getOrDefault((int) args[0], 0);
				case "getVarcIntValue":
					return varcs.getOrDefault((int) args[0], 0);
				case "getLocalPlayer":
					return player;
				case "getItemContainer":
					return items;
				case "getItemDefinition":
					return itemDefinition(itemNames.get((int) args[0]));
				case "getEnum":
					return enumComposition(enums.get((int) args[0]));
				default:
					return null;
			}
		});
	}

	private static Widget widget(boolean hidden, String text, int itemId, int quantity, Widget... children)
	{
		return proxy(Widget.class, (method, args) ->
		{
			switch (method)
			{
				case "isHidden":
					return hidden;
				case "getText":
					return text;
				case "getItemId":
					return itemId;
				case "getItemQuantity":
					return quantity;
				case "getDynamicChildren":
					return children;
				default:
					return null;
			}
		});
	}

	private static ItemComposition itemDefinition(String name)
	{
		return name == null ? null : proxy(ItemComposition.class,
			(method, args) -> "getName".equals(method) ? name : null);
	}

	private static EnumComposition enumComposition(Map<Integer, Integer> values)
	{
		return values == null ? null : proxy(EnumComposition.class,
			(method, args) -> "getIntValue".equals(method)
				? values.getOrDefault((int) args[0], Integer.MAX_VALUE)
				: null);
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
