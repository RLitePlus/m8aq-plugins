package net.runelite.client.plugins.m8aq.hotreload;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/** Verifies development plugin class selection. */
public class HotreloadServiceTest
{
	@Test
	public void selectsOnlyConcreteDescriptorPlugins()
	{
		assertTrue(HotreloadService.isPluginClass(FixturePlugin.class));
		assertFalse(HotreloadService.isPluginClass(MissingDescriptorPlugin.class));
		assertFalse(HotreloadService.isPluginClass(AbstractFixturePlugin.class));
		assertFalse(HotreloadService.isPluginClass(String.class));
	}

	@Test
	public void addsDeveloperModeOnce()
	{
		assertArrayEquals(new String[]{"--developer-mode"}, Launcher.withDeveloperMode(new String[0]));
		String[] configured = {"--developer-mode"};
		String[] unchanged = Launcher.withDeveloperMode(configured);
		assertArrayEquals(configured, unchanged);
		assertNotSame(configured, unchanged);
	}

	@Test
	public void startsEnabledByDefault()
	{
		assertTrue(HotreloadPlugin.class.getAnnotation(PluginDescriptor.class).enabledByDefault());
	}

	@PluginDescriptor(name = "Fixture")
	public static final class FixturePlugin extends Plugin
	{
	}

	public static final class MissingDescriptorPlugin extends Plugin
	{
	}

	@PluginDescriptor(name = "Abstract fixture")
	public abstract static class AbstractFixturePlugin extends Plugin
	{
	}
}
