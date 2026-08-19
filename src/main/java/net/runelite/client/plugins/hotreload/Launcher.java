package net.runelite.client.plugins.hotreload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import net.runelite.client.RuneLite;

/** Launches RuneLite and continuously rebuilds the default development plugin project. */
public final class Launcher
{
	/** System property containing the default development project directory. */
	public static final String PROJECT_DIR_PROPERTY = "hotreload.projectdir";

	private Launcher()
	{
	}

	/**
	 * Builds the development JAR, watches it for source changes, and starts RuneLite.
	 *
	 * @param args RuneLite command-line arguments
	 * @throws Exception when the development build or RuneLite cannot start
	 */
	public static void main(String[] args) throws Exception
	{
		configureDefaultTarget();
		RuneLite.main(withDeveloperMode(args));
	}

	static String[] withDeveloperMode(String[] args)
	{
		if (Arrays.asList(args).contains("--developer-mode"))
		{
			return args.clone();
		}

		String[] configured = Arrays.copyOf(args, args.length + 1);
		configured[args.length] = "--developer-mode";
		return configured;
	}

	private static void configureDefaultTarget() throws IOException, InterruptedException
	{
		String configuredJar = System.getProperty(HotreloadPlugin.DEV_JAR_PROPERTY);
		if (configuredJar != null && !configuredJar.trim().isEmpty())
		{
			return;
		}

		Path projectDir = defaultProjectDir();
		Path gradlew = gradleWrapper(projectDir);
		if (!Files.isRegularFile(gradlew) || (!isWindows() && !Files.isExecutable(gradlew)))
		{
			throw new IllegalStateException("Gradle wrapper is not executable: " + gradlew);
		}

		Process initialBuild = startGradle(projectDir, ":jar");
		int exitCode = initialBuild.waitFor();
		if (exitCode != 0)
		{
			throw new IllegalStateException("Development JAR build exited with " + exitCode);
		}

		Path devJar = projectDir.resolve("build/libs/m8aq-plugins.jar");
		if (!Files.isRegularFile(devJar))
		{
			throw new IllegalStateException("Development JAR was not produced: " + devJar);
		}
		System.setProperty(HotreloadPlugin.DEV_JAR_PROPERTY, devJar.toString());

		Process continuousBuild = startGradle(projectDir, ":jar", "--continuous", "--quiet");
		Runtime.getRuntime().addShutdownHook(new Thread(
			() -> stop(continuousBuild), "hotreload-build-shutdown"));
	}

	private static Path defaultProjectDir()
	{
		String configured = System.getProperty(PROJECT_DIR_PROPERTY);
		if (configured != null && !configured.trim().isEmpty())
		{
			return Paths.get(configured.trim()).toAbsolutePath();
		}
		return Paths.get(System.getProperty("user.home"), "Documents", "m8aq-plugins");
	}

	private static Process startGradle(Path projectDir, String... args) throws IOException
	{
		List<String> command = new ArrayList<>();
		if (isWindows())
		{
			command.add("cmd");
			command.add("/c");
		}
		command.add(gradleWrapper(projectDir).toString());
		command.add("--project-dir");
		command.add(projectDir.toString());
		command.addAll(Arrays.asList(args));
		ProcessBuilder builder = new ProcessBuilder(command)
			.inheritIO();
		builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
		return builder.start();
	}

	private static Path gradleWrapper(Path projectDir)
	{
		return projectDir.resolve(isWindows() ? "gradlew.bat" : "gradlew");
	}

	private static boolean isWindows()
	{
		return System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
	}

	private static void stop(Process process)
	{
		process.destroy();
		try
		{
			if (!process.waitFor(5, TimeUnit.SECONDS))
			{
				process.destroyForcibly();
			}
		}
		catch (InterruptedException ex)
		{
			Thread.currentThread().interrupt();
			process.destroyForcibly();
		}
	}
}
