package net.runelite.client.plugins.m8aq.hotreload;

import java.nio.file.Path;
import java.nio.file.Paths;
import javax.inject.Inject;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loads and reloads RuneLite plugins from a development JAR. */
@PluginDescriptor(
	name = "Hot Reload",
	description = "Reloads plugins from a rebuilt development JAR",
	tags = {"developer", "hotreload"},
	developerPlugin = true,
	enabledByDefault = true
)
public final class HotreloadPlugin extends Plugin
{
	/** System property containing the development JAR path. */
	public static final String DEV_JAR_PROPERTY = "hotreload.devjar";

	private static final Logger LOG = LoggerFactory.getLogger(HotreloadPlugin.class);

	@Inject
	private PluginManager pluginManager;

	@Inject
	private EventBus eventBus;

	private HotreloadService service;

	@Override
	protected void startUp()
	{
		String configured = System.getProperty(DEV_JAR_PROPERTY);
		if (configured == null || configured.trim().isEmpty())
		{
			LOG.info("{} is not set; Hotreload is idle", DEV_JAR_PROPERTY);
			return;
		}

		Path devJar = Paths.get(configured.trim()).toAbsolutePath();
		LOG.info("Watching development JAR {}", devJar);
		service = new HotreloadService(devJar, pluginManager, eventBus, getClass().getClassLoader());
		service.start();
	}

	@Override
	protected void shutDown()
	{
		if (service != null)
		{
			service.close();
			service = null;
		}
	}
}
