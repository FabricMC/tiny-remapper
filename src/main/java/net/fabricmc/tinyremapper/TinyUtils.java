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
import java.util.Objects;
import java.util.zip.GZIPInputStream;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingNsCompleter;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
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

	public static void read(BufferedReader reader, String fromNs, String toNs, MappingAcceptor out) throws IOException {
		MemoryMappingTree tree = new MemoryMappingTree();
		MappingReader.read(reader, new MappingNsCompleter(
				new MappingSourceNsSwitch(tree, fromNs, true), Collections.emptyMap()));
		String fromName;
		String toName;
		int classes = 0;
		int fields = 0;
		int methods = 0;
		int args = 0;
		int vars = 0;

		for (ClassMapping cls : tree.getClasses()) {
			if (!nullOrEqual(fromName = cls.getName(fromNs), toName = cls.getName(toNs))) {
				out.acceptClass(fromName, toName);
				classes++;
			}

			for (FieldMapping fld : cls.getFields()) {
				if (!nullOrEqual(fromName = fld.getName(fromNs), toName = fld.getName(toNs))) {
					out.acceptField(new Member(cls.getName(fromNs), fromName, fld.getDesc(fromNs)), toName);
					fields++;
				}
			}

			for (MethodMapping mth : cls.getMethods()) {
				Member member = new Member(cls.getName(fromNs), fromName = mth.getName(fromNs), mth.getDesc(fromNs));

				if (!nullOrEqual(fromName, toName = mth.getName(toNs))) {
					out.acceptMethod(member, toName);
					methods++;
				}
				
				for (MethodArgMapping arg : mth.getArgs()) {
					if (!nullOrEqual(arg.getName(fromNs), toName = arg.getName(toNs))) {
						out.acceptMethodArg(member, arg.getLvIndex(), toName);
						args++;
					}
				}
				
				for (MethodVarMapping var : mth.getVars()) {
					if (!nullOrEqual(var.getName(fromNs), toName = var.getName(toNs))) {
						out.acceptMethodVar(member, var.getLvIndex(), var.getStartOpIdx(), var.getLvtRowIndex(), toName);
						vars++;
					}
				}
			}
		}

		System.out.println(
			classes + " classes, "
			+ fields + " fields, "
			+ methods + " methods, "
			+ args + " args and "
			+ vars + " vars "
			+ "are about to be processed.");
	}

	private static boolean nullOrEqual(Object o1, Object o2) {
		return o1 == null
				|| o2 == null
				|| o1.equals(o2);
	}
}
