/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2016, 2022, FabricMC
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.zip.GZIPInputStream;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingNsCompleter;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree.ClassMapping;
import net.fabricmc.mappingio.tree.MappingTree.FieldMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodArgMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodVarMapping;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.IMappingProvider.MappingAcceptor;
import net.fabricmc.tinyremapper.IMappingProvider.Member;

public final class TinyUtils {
	private TinyUtils() { }

	public static IMappingProvider createTinyMappingProvider(final Path mappings, String fromM, String toM) {
		return out -> {
			try (BufferedReader reader = getMappingReader(mappings.toFile())) {
				read(reader, fromM, toM, out);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			//System.out.printf("%s: %d classes, %d methods, %d fields%n", mappings.getFileName().toString(), classMap.size(), methodMap.size(), fieldMap.size());
		};
	}

	private static BufferedReader getMappingReader(File file) throws IOException {
		InputStream is = new FileInputStream(file);

		if (file.getName().endsWith(".gz")) {
			is = new GZIPInputStream(is);
		}

		return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
	}

	public static IMappingProvider createTinyMappingProvider(final BufferedReader reader, String fromM, String toM) {
		return out -> {
			try {
				read(reader, fromM, toM, out);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			//System.out.printf("%d classes, %d methods, %d fields%n", classMap.size(), methodMap.size(), fieldMap.size());
		};
	}

	public static void read(BufferedReader reader, String from, String to, MappingAcceptor out) throws IOException {
		MemoryMappingTree tree = new MemoryMappingTree();
		MappingReader.read(reader, MappingFormat.TINY, new MappingNsCompleter(
				new MappingSourceNsSwitch(tree, from, true), Collections.emptyMap()));

		for (ClassMapping cls : tree.getClasses()) {
			out.acceptClass(cls.getName(from), cls.getName(to));

			for (FieldMapping fld : cls.getFields()) {
				out.acceptField(new Member(cls.getName(from), fld.getName(from), fld.getDesc(from)), fld.getName(to));
			}

			for (MethodMapping mth : cls.getMethods()) {
				Member member = new Member(cls.getName(from), mth.getName(from), mth.getDesc(from));
				out.acceptMethod(member, mth.getName(to));

				for (MethodArgMapping arg : mth.getArgs()) {
					out.acceptMethodArg(member, arg.getLvIndex(), arg.getName(to));
				}

				for (MethodVarMapping var : mth.getVars()) {
					out.acceptMethodVar(member, var.getLvIndex(), var.getStartOpIdx(), var.getLvtRowIndex(), var.getName(to));
				}
			}
		}
	}
}
