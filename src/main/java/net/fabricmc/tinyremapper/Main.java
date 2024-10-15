/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2016, 2023, FabricMC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.fabricmc.tinyremapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.regex.Pattern;

import net.fabricmc.tinyremapper.TinyRemapper.LinkedMethodPropagation;
import net.fabricmc.tinyremapper.api.TrLogger;

public class Main {
	public static void main(String[] rawArgs) {
		List<String> args = new ArrayList<String>(rawArgs.length);
		boolean ignoreFieldDesc = false;
		boolean propagatePrivate = false;
		LinkedMethodPropagation propagateBridges = LinkedMethodPropagation.DISABLED;
		boolean removeFrames = false;
		Set<String> forcePropagation = Collections.emptySet();
		File forcePropagationFile = null;
		Set<String> knownIndyBsm = new HashSet<>();
		File knownIndyBsmFile = null;
		boolean ignoreConflicts = false;
		boolean checkPackageAccess = false;
		boolean fixPackageAccess = false;
		boolean resolveMissing = false;
		boolean rebuildSourceFilenames = false;
		boolean skipLocalVariableMapping = false;
		boolean renameInvalidLocals = false;
		Pattern invalidLvNamePattern = null;
		boolean inferNameFromSameLvIndex = false;
		NonClassCopyMode ncCopyMode = NonClassCopyMode.FIX_META_INF;
		int threads = -1;

		Map<String, TinyRemapper.CLIExtensionProvider> providerMap = new HashMap<>();
		List<TinyRemapper.Extension> providedExtensions = new ArrayList<>();
		ServiceLoader<TinyRemapper.CLIExtensionProvider> cliProviderLoader =
				ServiceLoader.load(TinyRemapper.CLIExtensionProvider.class);
		cliProviderLoader.iterator().forEachRemaining(provider -> providerMap.put(provider.name(), provider));

		final ConsoleLogger logger = new ConsoleLogger();

		for (String arg : rawArgs) {
			if (arg.startsWith("--")) {
				int valueSepPos = arg.indexOf('=');

				String argKey = valueSepPos == -1 ? arg.substring(2) : arg.substring(2, valueSepPos);
				argKey = argKey.toLowerCase(Locale.ROOT);

				switch (argKey.toLowerCase()) {
				case "ignorefielddesc":
					ignoreFieldDesc = true;
					break;
				case "forcepropagation":
					forcePropagationFile = new File(arg.substring(valueSepPos + 1));
					break;
				case "knownindybsm":
					knownIndyBsmFile = new File(arg.substring(valueSepPos + 1));
				case "propagateprivate":
					propagatePrivate = true;
					break;
				case "propagatebridges":
					switch (arg.substring(valueSepPos + 1).toLowerCase(Locale.ENGLISH)) {
					case "disabled": propagateBridges = LinkedMethodPropagation.DISABLED; break;
					case "enabled": propagateBridges = LinkedMethodPropagation.ENABLED; break;
					case "compatible": propagateBridges = LinkedMethodPropagation.COMPATIBLE; break;
					default:
						logger.error("invalid propagateBridges: "+arg.substring(valueSepPos + 1));
						System.exit(1);
					}

					break;
				case "removeframes":
					removeFrames = true;
					break;
				case "ignoreconflicts":
					ignoreConflicts = true;
					break;
				case "checkpackageaccess":
					checkPackageAccess = true;
					break;
				case "fixpackageaccess":
					fixPackageAccess = true;
					break;
				case "resolvemissing":
					resolveMissing = true;
					break;
				case "rebuildsourcefilenames":
					rebuildSourceFilenames = true;
					break;
				case "skiplocalvariablemapping":
					skipLocalVariableMapping = true;
					break;
				case "renameinvalidlocals":
					renameInvalidLocals = true;
					break;
				case "invalidlvnamepattern":
					invalidLvNamePattern = Pattern.compile(arg.substring(valueSepPos + 1));
					break;
				case "infernamefromsamelvindex":
					inferNameFromSameLvIndex = true;
					break;
				case "nonclasscopymode":
					switch (arg.substring(valueSepPos + 1).toLowerCase(Locale.ENGLISH)) {
					case "unchanged": ncCopyMode = NonClassCopyMode.UNCHANGED; break;
					case "fixmeta": ncCopyMode = NonClassCopyMode.FIX_META_INF; break;
					case "skipmeta": ncCopyMode = NonClassCopyMode.SKIP_META_INF; break;
					default:
						logger.error("invalid nonClassCopyMode: "+arg.substring(valueSepPos + 1));
						System.exit(1);
					}

					break;
				case "threads":
					threads = Integer.parseInt(arg.substring(valueSepPos + 1));

					if (threads <= 0) {
						logger.error("Thread count must be > 0");
						System.exit(1);
					}

					break;
				case "mixin":
					handleExtension(providerMap, "mixin", providedExtensions);
					break;
				case "ext":
				case "extension":
					handleExtension(providerMap, arg.substring(valueSepPos + 1), providedExtensions);
					break;
				case "loglevel":
					logger.setLevel(TrLogger.Level.valueOf(arg.substring(valueSepPos + 1).toUpperCase(Locale.ENGLISH)));
					break;
				default:
					logger.error("invalid argument: "+arg+".");
					System.exit(1);
				}
			} else {
				args.add(arg);
			}
		}

		if (args.size() < 5) {
			logger.error("usage: <input> <output> <mappings> <from> <to> [<classpath>]... [--reverse] [--forcePropagation=<file>] [--propagatePrivate] [--ignoreConflicts]");
			System.exit(1);
		}

		Path input = Paths.get(args.get(0));

		if (!Files.isReadable(input)) {
			logger.error("Can't read input file "+input+".");
			System.exit(1);
		}

		Path output = Paths.get(args.get(1));
		Path mappings = Paths.get(args.get(2));

		if (!Files.isReadable(mappings) || Files.isDirectory(mappings)) {
			logger.error("Can't read mappings file "+mappings+".");
			System.exit(1);
		}

		String fromM = args.get(3);
		String toM = args.get(4);

		Path[] classpath = new Path[args.size() - 5];

		for (int i = 0; i < classpath.length; i++) {
			classpath[i] = Paths.get(args.get(i + 5));

			if (!Files.isReadable(classpath[i])) {
				logger.error("Can't read classpath file "+i+": "+classpath[i]+".");
				System.exit(1);
			}
		}

		if (forcePropagationFile != null) {
			forcePropagation = new HashSet<>();

			if (!forcePropagationFile.canRead()) {
				logger.error("Can't read forcePropagation file "+forcePropagationFile+".");
				System.exit(1);
			}

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(forcePropagationFile), StandardCharsets.UTF_8))) {
				String line;

				while ((line = reader.readLine()) != null) {
					line = line.trim();

					if (line.isEmpty() || line.charAt(0) == '#') continue;

					forcePropagation.add(line);
				}
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}

		if (knownIndyBsmFile != null) {
			if (!knownIndyBsmFile.canRead()) {
				logger.error("Can't read knownIndyBsm file "+knownIndyBsmFile+".");
				System.exit(1);
			}

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(knownIndyBsmFile), StandardCharsets.UTF_8))) {
				String line;

				while ((line = reader.readLine()) != null) {
					line = line.trim();

					if (line.isEmpty() || line.charAt(0) == '#') continue;

					knownIndyBsm.add(line);
				}
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}

		long startTime = System.nanoTime();

		TinyRemapper.Builder builder = TinyRemapper.newRemapper(logger)
				.withMappings(TinyUtils.createTinyMappingProvider(mappings, fromM, toM))
				.ignoreFieldDesc(ignoreFieldDesc)
				.withForcedPropagation(forcePropagation)
				.withKnownIndyBsm(knownIndyBsm)
				.propagatePrivate(propagatePrivate)
				.propagateBridges(propagateBridges)
				.removeFrames(removeFrames)
				.ignoreConflicts(ignoreConflicts)
				.checkPackageAccess(checkPackageAccess)
				.fixPackageAccess(fixPackageAccess)
				.resolveMissing(resolveMissing)
				.rebuildSourceFilenames(rebuildSourceFilenames)
				.skipLocalVariableMapping(skipLocalVariableMapping)
				.renameInvalidLocals(renameInvalidLocals)
				.invalidLvNamePattern(invalidLvNamePattern)
				.inferNameFromSameLvIndex(inferNameFromSameLvIndex)
				.threads(threads);

		for (TinyRemapper.Extension ext : providedExtensions) {
			ext.attach(builder);
		}

		TinyRemapper remapper = builder.build();

		try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(output).build()) {
			outputConsumer.addNonClassFiles(input, ncCopyMode, remapper);

			remapper.readInputs(input);
			remapper.readClassPath(classpath);

			remapper.apply(outputConsumer);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			remapper.finish();
		}

		logger.info("Finished after %.2f ms.", (System.nanoTime() - startTime) / 1e6);
	}

	private static void handleExtension(Map<String, TinyRemapper.CLIExtensionProvider> providerMap, String extName, List<TinyRemapper.Extension> providedExtensions) {
		TinyRemapper.CLIExtensionProvider provider = providerMap.get(extName);

		if (provider == null) {
			System.err.println("No such extension: " + extName);
			System.exit(1);
		}

		TinyRemapper.Extension extension = provider.provideExtension();

		if (extension != null) {
			providedExtensions.add(extension);
		}
	}
}
