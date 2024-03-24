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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;

import net.fabricmc.mappingio.FlatMappingVisitor;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.FlatAsRegularMappingVisitor;
import net.fabricmc.mappingio.adapter.MappingDstNsReorder;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.tree.MappingTreeView;
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

	public static IMappingProvider createMappingProvider(MappingTreeView tree, String fromM, String toM) {
		return out -> {
			try {
				tree.accept(createAdapter(fromM, toM, out));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		};
	}

	private static void read(BufferedReader reader, String fromNs, String toNs, MappingAcceptor out) throws IOException {
		MappingReader.read(reader, createAdapter(fromNs, toNs, out));
	}

	private static MappingVisitor createAdapter(String fromNs, String toNs, MappingAcceptor out) throws IOException {
		// Ensure fromNs is on source and toNs is on destination side
		return new MappingSourceNsSwitch(
				// Remove all dst namespaces we're not interested in
				new MappingDstNsReorder(
						new FlatAsRegularMappingVisitor(new MappingAdapter(out)),
						toNs),
				fromNs);
	}

	private static final class MappingAdapter implements FlatMappingVisitor {
		MappingAdapter(MappingAcceptor next) {
			this.next = next;
		}

		@Override
		public boolean visitClass(String srcName, String[] dstNames) throws IOException {
			String dstName = dstNames[0];
			if (!bothNullOrEqual(srcName, dstName)) next.acceptClass(srcName, dstName);
			return true;
		}

		@Override
		public boolean visitField(String srcClsName, String srcName, String srcDesc,
				String[] dstClsNames, String[] dstNames, String[] dstDescs) throws IOException {
			String dstName = dstNames[0];
			if (!bothNullOrEqual(srcName, dstName)) next.acceptField(new Member(srcClsName, srcName, srcDesc), dstName);
			return false;
		}

		@Override
		public boolean visitMethod(String srcClsName, String srcName, String srcDesc,
				String[] dstClsNames, String[] dstNames, String[] dstDescs) throws IOException {
			String dstName = dstNames[0];
			if (!bothNullOrEqual(srcName, dstName)) next.acceptMethod(new Member(srcClsName, srcName, srcDesc), dstName);
			return true;
		}

		@Override
		public boolean visitMethodArg(String srcClsName, String srcMethodName, String srcMethodDesc, int argPosition, int lvIndex,
				String srcArgName, String[] dstClsNames, String[] dstMethodNames, String[] dstMethodDescs, String[] dstArgNames) throws IOException {
			String dstName = dstArgNames[0];
			if (!firstNullOrEqual(dstName, srcArgName)) next.acceptMethodArg(new Member(srcClsName, srcMethodName, srcMethodDesc), lvIndex, dstName);
			return false;
		}

		@Override
		public boolean visitMethodVar(String srcClsName, String srcMethodName, String srcMethodDesc, int lvtRowIndex, int lvIndex, int startOpIdx,
				int endOpIds, String srcVarName, String[] dstClsNames, String[] dstMethodNames, String[] dstMethodDescs, String[] dstVarNames) throws IOException {
			String dstName = dstVarNames[0];
			if (!firstNullOrEqual(dstName, srcVarName)) next.acceptMethodVar(new Member(srcClsName, srcMethodName, srcMethodDesc), lvIndex, startOpIdx, -1, dstName);
			return false;
		}

		@Override
		public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) throws IOException { }
		@Override
		public void visitClassComment(String srcName, String[] dstNames, String comment) throws IOException { }
		@Override
		public void visitFieldComment(String srcClsName, String srcName, String srcDesc, String[] dstClsNames, String[] dstNames, String[] dstDescs, String comment) throws IOException { }
		@Override
		public void visitMethodComment(String srcClsName, String srcName, String srcDesc, String[] dstClsNames, String[] dstNames, String[] dstDescs, String comment) throws IOException { }
		@Override
		public void visitMethodArgComment(String srcClsName, String srcMethodName, String srcMethodDesc, int argPosition, int lvIndex, String srcArgName, String[] dstClsNames, String[] dstMethodNames, String[] dstMethodDescs, String[] dstArgNames, String comment) throws IOException { }
		@Override
		public void visitMethodVarComment(String srcClsName, String srcMethodName, String srcMethodDesc, int lvtRowIndex, int lvIndex, int startOpIdx, int endOpIdx, String srcVarName, String[] dstClsNames, String[] dstMethodNames, String[] dstMethodDescs, String[] dstVarNames, String comment) throws IOException { }

		private final MappingAcceptor next;
	}

	private static boolean firstNullOrEqual(Object o1, Object o2) {
		return o1 == null || o1.equals(o2);
	}

	private static boolean bothNullOrEqual(Object o1, Object o2) {
		return o2 == null || firstNullOrEqual(o1, o2);
	}
}
