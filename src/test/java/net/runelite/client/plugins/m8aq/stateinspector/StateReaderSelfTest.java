package net.runelite.client.plugins.m8aq.stateinspector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.plugins.m8aq.api.smithing.Anvil;
import net.runelite.client.plugins.m8aq.api.smithing.BlastFurnace;
import net.runelite.client.plugins.m8aq.api.smithing.Furnace;

/** Minimal runnable checks for {@link StateReader}. */
public final class StateReaderSelfTest
{
	private StateReaderSelfTest()
	{
	}

	/** Runs the checks with Java assertions enabled. */
	public static void main(String[] args) throws Exception
	{
		assert StateInspectorPlugin.class.getPackageName().equals(
			"net.runelite.client.plugins.m8aq.stateinspector");
		List<Class<?>> discovered = StateInspectorPlugin.discoverApiClasses(
			StateInspectorPlugin.class.getClassLoader());
		assert discovered.containsAll(Arrays.asList(Anvil.class, BlastFurnace.class, Furnace.class));
		for (int index = 1; index < discovered.size(); index++)
		{
			assert discovered.get(index - 1).getName().compareTo(discovered.get(index).getName()) < 0;
		}
		for (Class<?> apiClass : discovered)
		{
			assert apiClass.getMethod("getState", Client.class) != null;
		}

		Map<String, String> values = StateReader.read(FakeApi.class, null);
		List<String> names = new ArrayList<>(values.keySet());
		assert names.equals(Arrays.asList(
			"canCollect", "getBroken", "getCount", "getNested", "isReady", "needsCooling"));
		assert values.get("getCount").equals("28");
		assert values.get("getNested").equals(
			"{\"entry\":{\"value\":7,\"name\":null,\"label\":\"V's <shield> & gear = safe\"}}");
		assert values.get("getBroken").equals("<IllegalStateException: broken>");

		SwingUtilities.invokeAndWait(() ->
		{
			List<Class<?>> refreshed = new ArrayList<>();
			StateInspectorPanel panel = new StateInspectorPanel(
				Arrays.asList(FakeApi.class), refreshed::add);
			assert !panel.isActive();
			panel.onActivate();
			assert panel.isActive();
			assert refreshed.equals(Arrays.asList(FakeApi.class));
			panel.onDeactivate();
			assert !panel.isActive();
		});
	}

	/** Fake API root following the inspector convention. */
	public static final class FakeApi
	{
		private FakeApi()
		{
		}

		/** @return fake snapshot */
		public static FakeState getState(Client client)
		{
			return new FakeState();
		}
	}

	/** Fake state containing included and excluded methods. */
	public static final class FakeState
	{
		/** @return count */
		public int getCount()
		{
			return 28;
		}

		/** @return nested value without a custom string representation */
		public Map<String, FakeValue> getNested()
		{
			return Map.of("entry", new FakeValue(7));
		}

		/** @return readiness */
		public boolean isReady()
		{
			return true;
		}

		/** @return collectability */
		public boolean canCollect()
		{
			return true;
		}

		/** @return cooling requirement */
		public boolean needsCooling()
		{
			return false;
		}

		/** @return never returns */
		public String getBroken()
		{
			throw new IllegalStateException("broken");
		}

		/** @return ignored helper */
		public int helper()
		{
			return 0;
		}

		/** @return ignored parameterized accessor */
		public int getIndexed(int index)
		{
			return index;
		}
	}

	/** Nested inspector value. */
	public static final class FakeValue
	{
		private final int value;
		private final String name;
		private final String label;

		private FakeValue(int value)
		{
			this.value = value;
			this.name = null;
			this.label = "V's <shield> & gear = safe";
		}
	}
}
