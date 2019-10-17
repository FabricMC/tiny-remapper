/*
 * Copyright (C) 2016, 2018 Player, asie
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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.CheckClassAdapter;

import net.fabricmc.tinyremapper.IMappingProvider.MappingAcceptor;
import net.fabricmc.tinyremapper.IMappingProvider.Member;
import net.fabricmc.tinyremapper.MemberInstance.MemberType;

public class TinyRemapper {
	public static class Builder {
		private Builder() { }

		public Builder withMappings(IMappingProvider provider) {
			mappingProviders.add(provider);
			return this;
		}

		public Builder ignoreFieldDesc(boolean value) {
			this.ignoreFieldDesc = value;
			return this;
		}

		public Builder threads(int threadCount) {
			this.threadCount = threadCount;
			return this;
		}

		public Builder withForcedPropagation(Set<String> entries) {
			forcePropagation.addAll(entries);
			return this;
		}

		public Builder propagatePrivate(boolean value) {
			propagatePrivate = value;
			return this;
		}

		public Builder removeFrames(boolean value) {
			removeFrames = value;
			return this;
		}

		public Builder ignoreConflicts(boolean value) {
			ignoreConflicts = value;
			return this;
		}

		public Builder resolveMissing(boolean value) {
			resolveMissing = value;
			return this;
		}

		public Builder checkPackageAccess(boolean value) {
			checkPackageAccess = value;
			return this;
		}

		public Builder fixPackageAccess(boolean value) {
			fixPackageAccess = value;
			return this;
		}

		public Builder rebuildSourceFilenames(boolean value) {
			rebuildSourceFilenames = value;
			return this;
		}

		public Builder renameInvalidLocals(boolean value) {
			renameInvalidLocals = value;
			return this;
		}

		public TinyRemapper build() {
			TinyRemapper remapper = new TinyRemapper(mappingProviders, ignoreFieldDesc, threadCount,
					forcePropagation, propagatePrivate,
					removeFrames, ignoreConflicts, resolveMissing, checkPackageAccess || fixPackageAccess, fixPackageAccess,
					rebuildSourceFilenames, renameInvalidLocals);

			return remapper;
		}

		private final Set<IMappingProvider> mappingProviders = new HashSet<>();
		private boolean ignoreFieldDesc;
		private int threadCount;
		private final Set<String> forcePropagation = new HashSet<>();
		private boolean propagatePrivate = false;
		private boolean removeFrames = false;
		private boolean ignoreConflicts = false;
		private boolean resolveMissing = false;
		private boolean checkPackageAccess = false;
		private boolean fixPackageAccess = false;
		private boolean rebuildSourceFilenames = false;
		private boolean renameInvalidLocals = false;
	}

	private TinyRemapper(Collection<IMappingProvider> mappingProviders, boolean ignoreFieldDesc,
			int threadCount,
			Set<String> forcePropagation, boolean propagatePrivate,
			boolean removeFrames,
			boolean ignoreConflicts,
			boolean resolveMissing,
			boolean checkPackageAccess,
			boolean fixPackageAccess,
			boolean rebuildSourceFilenames,
			boolean renameInvalidLocals) {
		this.mappingProviders = mappingProviders;
		this.ignoreFieldDesc = ignoreFieldDesc;
		this.threadCount = threadCount > 0 ? threadCount : Math.max(Runtime.getRuntime().availableProcessors(), 2);
		this.threadPool = Executors.newFixedThreadPool(this.threadCount);
		this.forcePropagation = forcePropagation;
		this.propagatePrivate = propagatePrivate;
		this.removeFrames = removeFrames;
		this.ignoreConflicts = ignoreConflicts;
		this.resolveMissing = resolveMissing;
		this.checkPackageAccess = checkPackageAccess;
		this.fixPackageAccess = fixPackageAccess;
		this.rebuildSourceFilenames = rebuildSourceFilenames;
		this.renameInvalidLocals = renameInvalidLocals;
	}

	public static Builder newRemapper() {
		return new Builder();
	}

	public void finish() {
		threadPool.shutdown();
		try {
			threadPool.awaitTermination(20, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void readInputs(final Path... inputs) {
		read(inputs, true);
	}

	public void readClassPath(final Path... inputs) {
		read(inputs, false);
	}

	private void read(Path[] inputs, boolean isInput) {
		List<Future<List<ClassInstance>>> futures = new ArrayList<>();
		List<FileSystem> fsToClose = Collections.synchronizedList(new ArrayList<>());

		try {
			for (Path input : inputs) {
				futures.addAll(read(input, isInput, true, fsToClose));
			}

			if (futures.size() > 0) {
				dirty = true;
			}

			for (Future<List<ClassInstance>> future : futures) {
				for (ClassInstance node : future.get()) {
					ClassInstance prev = classes.put(node.getName(), node);

					if (prev != null) {
						if (isInput && !prev.isInput) {
							System.out.printf("duplicate input class %s, from %s and %s", node.getName(), prev.srcPath, node.srcPath);
						} else if (!isInput && prev.isInput) { // give the input class priority
							classes.put(prev.getName(), prev);
						}
					}
				}
			}
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		} finally {
			for (FileSystem fs : fsToClose) {
				try {
					fs.close();
				} catch (IOException e) { }
			}
		}
	}

	private List<Future<List<ClassInstance>>> read(final Path file, boolean isInput, boolean saveData, final List<FileSystem> fsToClose) {
		try {
			return read(file, isInput, file, saveData, fsToClose);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private List<Future<List<ClassInstance>>> read(final Path file, boolean isInput, final Path srcPath, final boolean saveData, final List<FileSystem> fsToClose) throws IOException {
		List<Future<List<ClassInstance>>> ret = new ArrayList<>();

		Files.walkFileTree(file, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				String name = file.getFileName().toString();

				if (name.endsWith(".jar") ||
						name.endsWith(".zip") ||
						name.endsWith(".class")) {
					ret.add(threadPool.submit(new Callable<List<ClassInstance>>() {
						@Override
						public List<ClassInstance> call() {
							try {
								return readFile(file, isInput, srcPath, saveData, fsToClose);
							} catch (URISyntaxException e) {
								throw new RuntimeException(e);
							} catch (IOException e) {
								System.out.println(file.toAbsolutePath());
								e.printStackTrace();
								return Collections.emptyList();
							}
						}
					}));
				}

				return FileVisitResult.CONTINUE;
			}
		});

		return ret;
	}

	private List<ClassInstance> readFile(Path file, boolean isInput, final Path srcPath, boolean saveData, List<FileSystem> fsToClose) throws IOException, URISyntaxException {
		List<ClassInstance> ret = new ArrayList<ClassInstance>();

		if (file.toString().endsWith(".class")) {
			ret.add(analyze(isInput, srcPath, Files.readAllBytes(file), saveData));
		} else {
			URI uri = new URI("jar:"+file.toUri().toString());
			FileSystem fs = null;

			try {
				fs = FileSystems.getFileSystem(uri);
			} catch (FileSystemNotFoundException e) {

			}

			if (fs == null) {
				fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
				fsToClose.add(fs);
			}

			Files.walkFileTree(fs.getPath("/"), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.toString().endsWith(".class")) {
						ret.add(analyze(isInput, srcPath, Files.readAllBytes(file), saveData));
					}

					return FileVisitResult.CONTINUE;
				}
			});
		}

		return ret;
	}

	private ClassInstance analyze(boolean isInput, Path srcPath, byte[] data, boolean saveData) {
		final ClassInstance ret = new ClassInstance(this, isInput, srcPath, saveData ? data : null);

		ClassReader reader = new ClassReader(data);

		reader.accept(new ClassVisitor(Opcodes.ASM7) {
			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				ret.init(name, superName, access, interfaces);
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
				MemberInstance prev = ret.addMember(new MemberInstance(MemberType.METHOD, ret, name, desc, access));
				if (prev != null) throw new RuntimeException(String.format("duplicate method %s/%s%s in inputs", ret.getName(), name, desc));

				return null;
			}

			@Override
			public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
				MemberInstance prev = ret.addMember(new MemberInstance(MemberType.FIELD, ret, name, desc, access));
				if (prev != null) throw new RuntimeException(String.format("duplicate field %s/%s;;%s in inputs", ret.getName(), name, desc));

				return null;
			}
		}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE);

		return ret;
	}

	String mapClass(String className) {
		String ret = classMap.get(className);

		return ret != null ? ret : className;
	}

	private void loadMappings() {
		MappingAcceptor acceptor = new MappingAcceptor() {
			@Override
			public void acceptClass(String srcName, String dstName) {
				if (srcName == null) throw new NullPointerException("null src name");
				if (dstName == null) throw new NullPointerException("null dst name");

				classMap.put(srcName, dstName);
			}

			@Override
			public void acceptMethod(Member method, String dstName) {
				if (method == null) throw new NullPointerException("null src method");
				if (method.owner == null) throw new NullPointerException("null src method owner");
				if (method.name == null) throw new NullPointerException("null src method name");
				if (method.desc == null) throw new NullPointerException("null src method desc");
				if (dstName == null) throw new NullPointerException("null dst name");

				methodMap.put(method.owner+"/"+MemberInstance.getMethodId(method.name, method.desc), dstName);
			}

			@Override
			public void acceptMethodArg(Member method, int lvIndex, String dstName) {
				if (method == null) throw new NullPointerException("null src method");
				if (method.owner == null) throw new NullPointerException("null src method owner");
				if (method.name == null) throw new NullPointerException("null src method name");
				if (method.desc == null) throw new NullPointerException("null src method desc");
				if (dstName == null) throw new NullPointerException("null dst name");

				methodArgMap.put(method.owner+"/"+MemberInstance.getMethodId(method.name, method.desc)+lvIndex, dstName);
			}

			@Override
			public void acceptMethodVar(Member method, int lvIndex, int startOpIdx, int asmIndex, String dstName) {
				if (method == null) throw new NullPointerException("null src method");
				if (method.owner == null) throw new NullPointerException("null src method owner");
				if (method.name == null) throw new NullPointerException("null src method name");
				if (method.desc == null) throw new NullPointerException("null src method desc");
				if (dstName == null) throw new NullPointerException("null dst name");

				// TODO Auto-generated method stub

			}

			@Override
			public void acceptField(Member field, String dstName) {
				if (field == null) throw new NullPointerException("null src field");
				if (field.owner == null) throw new NullPointerException("null src field owner");
				if (field.name == null) throw new NullPointerException("null src field name");
				if (field.desc == null && !ignoreFieldDesc) throw new NullPointerException("null src field desc");
				if (dstName == null) throw new NullPointerException("null dst name");

				fieldMap.put(field.owner+"/"+MemberInstance.getFieldId(field.name, field.desc, ignoreFieldDesc), dstName);
			}
		};

		for (IMappingProvider provider : mappingProviders) {
			provider.load(acceptor);
		}
	}

	private void checkClassMappings() {
		Set<String> testSet = new HashSet<>(classMap.values());

		if (testSet.size() != classMap.size()) {
			System.out.println("non-unique class target name mappings:");

			for (Map.Entry<String, String> e : classMap.entrySet()) {
				if (!testSet.remove(e.getValue())) {
					System.out.printf("  %s -> %s%n", e.getKey(), e.getValue());
				}
			}

			throw new RuntimeException("duplicate class target name mappings detected");
		}
	}

	private void merge() {
		for (ClassInstance node : classes.values()) {
			assert node.getSuperName() != null;

			ClassInstance parent = classes.get(node.getSuperName());

			if (parent != null) {
				node.parents.add(parent);
				parent.children.add(node);
			}

			for (String iface : node.getInterfaces()) {
				parent = classes.get(iface);

				if (parent != null) {
					node.parents.add(parent);
					parent.children.add(node);
				}
			}
		}
	}

	private void propagate() {
		List<Future<?>> futures = new ArrayList<>();
		List<Map.Entry<String, String>> tasks = new ArrayList<>();
		int maxTasks = methodMap.size() / threadCount / 4;

		for (Map.Entry<String, String> entry : methodMap.entrySet()) {
			tasks.add(entry);

			if (tasks.size() >= maxTasks) {
				futures.add(threadPool.submit(new Propagation(MemberType.METHOD, tasks)));
				tasks.clear();
			}
		}

		futures.add(threadPool.submit(new Propagation(MemberType.METHOD, tasks)));
		tasks.clear();

		for (Map.Entry<String, String> entry : fieldMap.entrySet()) {
			tasks.add(entry);

			if (tasks.size() >= maxTasks) {
				futures.add(threadPool.submit(new Propagation(MemberType.FIELD, tasks)));
				tasks.clear();
			}
		}

		futures.add(threadPool.submit(new Propagation(MemberType.FIELD, tasks)));
		tasks.clear();

		waitForAll(futures);

		handleConflicts();
	}

	private void handleConflicts() {
		Set<String> testSet = new HashSet<>();
		boolean targetNameCheckFailed = false;

		for (ClassInstance cls : classes.values()) {
			for (MemberInstance member : cls.getMembers()) {
				String name = member.getNewName();
				if (name == null) name = member.name;

				testSet.add(MemberInstance.getId(member.type, name, member.desc, ignoreFieldDesc));
			}

			if (testSet.size() != cls.getMembers().size()) {
				if (!targetNameCheckFailed) {
					targetNameCheckFailed = true;
					System.out.println("Mapping target name conflicts detected:");
				}

				Map<String, List<MemberInstance>> duplicates = new HashMap<>();

				for (MemberInstance member : cls.getMembers()) {
					String name = member.getNewName();
					if (name == null) name = member.name;

					duplicates.computeIfAbsent(MemberInstance.getId(member.type, name, member.desc, ignoreFieldDesc), ignore -> new ArrayList<>()).add(member);
				}

				for (Map.Entry<String, List<MemberInstance>> e : duplicates.entrySet()) {
					String nameDesc = e.getKey();
					List<MemberInstance> members = e.getValue();
					if (members.size() < 2) continue;

					MemberInstance anyMember = members.get(0);
					System.out.printf("  %ss %s/[", anyMember.type, cls.getName());

					for (int i = 0; i < members.size(); i++) {
						if (i != 0) System.out.print(", ");

						MemberInstance member = members.get(i);

						if (member.newNameOriginatingCls != null && !member.newNameOriginatingCls.equals(cls.getName())) {
							System.out.print(member.newNameOriginatingCls);
							System.out.print('/');
						}

						System.out.print(member.name);
					}

					System.out.printf("]%s -> %s%n", MemberInstance.getId(anyMember.type, "", anyMember.desc, ignoreFieldDesc), MemberInstance.getNameFromId(anyMember.type, nameDesc, ignoreFieldDesc));
				}
			}

			testSet.clear();
		}

		boolean unfixableConflicts = false;

		if (!conflicts.isEmpty()) {
			System.out.println("Mapping source name conflicts detected:");

			for (Map.Entry<MemberInstance, Set<String>> entry : conflicts.entrySet()) {
				MemberInstance member = entry.getKey();
				String newName = member.getNewName();
				Set<String> names = entry.getValue();
				names.add(member.cls.getName()+"/"+newName);

				System.out.printf("  %s %s %s (%s) -> %s%n", member.cls.getName(), member.type.name(), member.name, member.desc, names);

				if (ignoreConflicts) {
					Map<String, String> mappings = member.type == MemberType.METHOD ? methodMap : fieldMap;
					String mappingName = mappings.get(member.cls.getName()+"/"+member.getId());

					if (mappingName == null) { // no direct mapping match, try parents
						Queue<ClassInstance> queue = new ArrayDeque<>(member.cls.parents);
						ClassInstance cls;

						while ((cls = queue.poll()) != null) {
							mappingName = mappings.get(cls.getName()+"/"+member.getId());
							if (mappingName != null) break;

							queue.addAll(cls.parents);
						}
					}

					if (mappingName == null) {
						unfixableConflicts = true;
					} else {
						member.forceSetNewName(mappingName);
						System.out.println("    fixable: replaced with "+mappingName);
					}
				}
			}
		}

		if (!conflicts.isEmpty() && !ignoreConflicts || unfixableConflicts || targetNameCheckFailed) {
			if (ignoreConflicts || targetNameCheckFailed) System.out.println("There were unfixable conflicts.");

			System.exit(1);
		}
	}

	public void apply(final BiConsumer<String, byte[]> outputConsumer) {
		if (dirty) {
			loadMappings();
			checkClassMappings();
			merge();
			propagate();

			dirty = false;
		}

		Map<ClassInstance, byte[]> outputBuffer;
		BiConsumer<ClassInstance, byte[]> immediateOutputConsumer;

		if (fixPackageAccess) {
			outputBuffer = new ConcurrentHashMap<>();
			immediateOutputConsumer = outputBuffer::put;
		} else {
			outputBuffer = null;
			immediateOutputConsumer = (cls, data) -> outputConsumer.accept(mapClass(cls.getName()), data);
		}

		List<Future<?>> futures = new ArrayList<>();

		for (final ClassInstance cls : classes.values()) {
			if (!cls.isInput) continue;

			futures.add(threadPool.submit(() -> immediateOutputConsumer.accept(cls, apply(cls))));
		}

		waitForAll(futures);

		boolean needsFixes = !classesToMakePublic.isEmpty() || !membersToMakePublic.isEmpty();

		if (fixPackageAccess) {
			if (needsFixes) {
				System.out.printf("Fixing access for %d classes and %d members.%n", classesToMakePublic.size(), membersToMakePublic.size());
			}

			for (Map.Entry<ClassInstance, byte[]> entry : outputBuffer.entrySet()) {
				ClassInstance cls = entry.getKey();
				byte[] data = entry.getValue();

				if (needsFixes) {
					data = fixClass(cls, data);
				}

				outputConsumer.accept(mapClass(cls.getName()), data);
			}

			classesToMakePublic.clear();
			membersToMakePublic.clear();
		} else if (needsFixes) {
			throw new RuntimeException(String.format("%d classes and %d members need access fixes", classesToMakePublic.size(), membersToMakePublic.size()));
		}
	}

	private byte[] apply(final ClassInstance cls) {
		ClassReader reader = new ClassReader(cls.data);
		ClassWriter writer = new ClassWriter(0);
		int flags = removeFrames ? ClassReader.SKIP_FRAMES : ClassReader.EXPAND_FRAMES;

		ClassVisitor visitor = writer;

		if (rebuildSourceFilenames) {
			visitor = new SourceNameRebuildVisitor(Opcodes.ASM7, visitor);
		}

		if (check) {
			//noinspection UnusedAssignment
			visitor = new CheckClassAdapter(visitor);
		}

		reader.accept(new AsmClassRemapper(visitor, remapper, checkPackageAccess, renameInvalidLocals), flags);
		// TODO: compute frames (-Xverify:all -XX:-FailOverToOldVerifier)

		return writer.toByteArray();
	}

	private byte[] fixClass(ClassInstance cls, byte[] data) {
		boolean makeClsPublic = classesToMakePublic.contains(cls);
		Set<String> clsMembersToMakePublic = null;

		for (MemberInstance member : cls.getMembers()) {
			if (membersToMakePublic.contains(member)) {
				if (clsMembersToMakePublic == null) clsMembersToMakePublic = new HashSet<>();

				String mappedName, mappedDesc;

				if (member.type == MemberType.FIELD) {
					mappedName = remapper.mapFieldName(cls.getName(), member.name, member.desc);
					mappedDesc = remapper.mapDesc(member.desc);
				} else {
					mappedName = remapper.mapMethodName(cls.getName(), member.name, member.desc);
					mappedDesc = remapper.mapMethodDesc(member.desc);
				}

				clsMembersToMakePublic.add(MemberInstance.getId(member.type, mappedName, mappedDesc, ignoreFieldDesc));
			}
		}

		if (!makeClsPublic && clsMembersToMakePublic == null) return data;

		final Set<String> finalClsMembersToMakePublic = clsMembersToMakePublic;

		ClassReader reader = new ClassReader(data);
		ClassWriter writer = new ClassWriter(0);

		reader.accept(new ClassVisitor(Opcodes.ASM7, writer) {
			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				if (makeClsPublic) {
					access = (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
				}

				super.visit(version, access, name, signature, superName, interfaces);
			}

			@Override
			public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
				if (finalClsMembersToMakePublic != null
						&& finalClsMembersToMakePublic.contains(MemberInstance.getFieldId(name, descriptor, ignoreFieldDesc))) {
					access = (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
				}

				return super.visitField(access, name, descriptor, signature, value);
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				if (finalClsMembersToMakePublic != null
						&& finalClsMembersToMakePublic.contains(MemberInstance.getMethodId(name, descriptor))) {
					access = (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
				}

				return super.visitMethod(access, name, descriptor, signature, exceptions);
			}
		}, 0);

		return writer.toByteArray();
	}

	private static void waitForAll(Iterable<Future<?>> futures) {
		try {
			for (Future<?> future : futures) {
				future.get();
			}
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	private static String getClassName(String nameDesc, MemberType type) {
		int descStart = getDescStart(nameDesc, type);
		int nameStart = nameDesc.lastIndexOf('/', descStart - 1);
		if (nameStart == -1) nameStart = 0;

		return nameDesc.substring(0, nameStart);
	}

	private static String stripClassName(String nameDesc, MemberType type) {
		int descStart = getDescStart(nameDesc, type);
		int nameStart = nameDesc.lastIndexOf('/', descStart - 1);
		if (nameStart == -1) nameStart = 0;

		return nameDesc.substring(nameStart + 1);
	}

	private static int getDescStart(String nameDesc, MemberType type) {
		int ret;

		if (type == MemberType.METHOD) {
			ret = nameDesc.indexOf('(');
		} else {
			ret = nameDesc.indexOf(";;");
		}

		if (ret == -1) ret = nameDesc.length();

		return ret;
	}

	enum Direction {
		ANY,
		UP,
		DOWN
	}

	class Propagation implements Runnable {
		Propagation(MemberType type, List<Map.Entry<String, String> > tasks) {
			this.type = type;
			this.tasks.addAll(tasks);
		}

		@Override
		public void run() {
			Set<ClassInstance> visitedUp = Collections.newSetFromMap(new IdentityHashMap<>());
			Set<ClassInstance> visitedDown = Collections.newSetFromMap(new IdentityHashMap<>());

			for (Map.Entry<String, String> entry : tasks) {
				String className = getClassName(entry.getKey(), type);
				ClassInstance cls = classes.get(className);
				if (cls == null) continue; // not available for this Side

				String idSrc = stripClassName(entry.getKey(), type);
				String nameDst = entry.getValue();
				assert nameDst.indexOf('/') < 0;

				if (MemberInstance.getNameFromId(type, idSrc, ignoreFieldDesc).equals(nameDst)) {
					continue; // no name change
				}

				MemberInstance member = resolveMissing ? cls.resolve(type, idSrc) : cls.getMember(type, idSrc);

				if (member == null) {
					// not available for this Side
					continue;
				}

				cls = member.cls;
				boolean isVirtual = member.isVirtual();

				visitedUp.add(cls);
				visitedDown.add(cls);
				cls.propagate(TinyRemapper.this, type, className, idSrc, nameDst, isVirtual ? Direction.ANY : Direction.DOWN, isVirtual, true, visitedUp, visitedDown);
				visitedUp.clear();
				visitedDown.clear();
			}
		}

		private final MemberType type;
		private final List<Map.Entry<String, String> > tasks = new ArrayList<Map.Entry<String,String> >();
	}

	private final boolean check = false;

	final Set<String> forcePropagation;
	final boolean propagatePrivate;
	private final boolean removeFrames;
	private final boolean ignoreConflicts;
	private final boolean resolveMissing;
	private final boolean checkPackageAccess;
	private final boolean fixPackageAccess;
	private final boolean rebuildSourceFilenames;
	private final boolean renameInvalidLocals;
	final Map<String, String> classMap = new HashMap<>();
	final Map<String, String> methodMap = new HashMap<>();
	final Map<String, String> methodArgMap = new HashMap<>();
	final Map<String, String> fieldMap = new HashMap<>();
	final Map<String, ClassInstance> classes = new HashMap<>();
	final Map<MemberInstance, Set<String>> conflicts = new ConcurrentHashMap<>();
	final Set<ClassInstance> classesToMakePublic = Collections.newSetFromMap(new ConcurrentHashMap<>());
	final Set<MemberInstance> membersToMakePublic = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private final Collection<IMappingProvider> mappingProviders;
	final boolean ignoreFieldDesc;
	private final int threadCount;
	private final ExecutorService threadPool;
	private final AsmRemapper remapper = new AsmRemapper(this);

	private boolean dirty;
}
