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
import java.util.concurrent.ConcurrentMap;
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

public class TinyRemapper {
	public static class Builder {
		private Builder() { }

		public Builder withMappings(IMappingProvider provider) {
			mappingProviders.add(provider);
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

		public Builder rebuildSourceFilenames(boolean value) {
			rebuildSourceFilenames = value;
			return this;
		}

		public Builder renameInvalidLocals(boolean value) {
			renameInvalidLocals = value;
			return this;
		}

		public TinyRemapper build() {
			TinyRemapper remapper = new TinyRemapper(threadCount, forcePropagation, propagatePrivate, removeFrames, ignoreConflicts, resolveMissing, rebuildSourceFilenames, renameInvalidLocals);

			for (IMappingProvider provider : mappingProviders) {
				provider.load(remapper.classMap, remapper.fieldMap, remapper.methodMap);
			}

			return remapper;
		}

		private int threadCount;
		private final Set<String> forcePropagation = new HashSet<>();
		private final Set<IMappingProvider> mappingProviders = new HashSet<>();
		private boolean propagatePrivate = false;
		private boolean removeFrames = false;
		private boolean ignoreConflicts = false;
		private boolean resolveMissing = false;
		private boolean rebuildSourceFilenames = false;
		private boolean renameInvalidLocals = false;
	}

	private TinyRemapper(int threadCount, Set<String> forcePropagation,
			boolean propagatePrivate,
			boolean removeFrames,
			boolean ignoreConflicts,
			boolean resolveMissing,
		    boolean rebuildSourceFilenames,
			boolean renameInvalidLocals) {
		this.threadCount = threadCount > 0 ? threadCount : Math.max(Runtime.getRuntime().availableProcessors(), 2);
		this.threadPool = Executors.newFixedThreadPool(this.threadCount);
		this.forcePropagation = forcePropagation;
		this.propagatePrivate = propagatePrivate;
		this.removeFrames = removeFrames;
		this.ignoreConflicts = ignoreConflicts;
		this.resolveMissing = resolveMissing;
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

	public void read(final Path... inputs) {
		List<Future<List<TinyRemapper.RClass>>> futures = new ArrayList<>();
		List<FileSystem> fsToClose = Collections.synchronizedList(new ArrayList<>());

		try {
			for (Path input : inputs) {
				futures.addAll(read(input, TinyRemapper.Namespace.Unknown, true, fsToClose));
			}

			if (futures.size() > 0) {
				dirty = true;
			}

			for (Future<List<TinyRemapper.RClass> > future : futures) {
				for (TinyRemapper.RClass node : future.get()) {
					nodes.put(node.name, node);
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

	private List<Future<List<RClass>>> read(final Path file, final Namespace namespace, boolean saveData, final List<FileSystem> fsToClose) {
		try {
			return read(file, namespace, file, saveData, fsToClose);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private List<Future<List<RClass>>> read(final Path file, final Namespace namespace, final Path srcPath, final boolean saveData, final List<FileSystem> fsToClose) throws IOException {
		List<Future<List<RClass>>> ret = new ArrayList<>();

		Files.walkFileTree(file, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				String name = file.getFileName().toString();

				if (name.endsWith(".jar") ||
						name.endsWith(".zip") ||
						name.endsWith(".class")) {
					ret.add(threadPool.submit(new Callable<List<RClass>>() {
						@Override
						public List<RClass> call() {
							try {
								return readFile(file, namespace, srcPath, saveData, fsToClose);
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

	private List<RClass> readFile(Path file, Namespace namespace, final Path srcPath, boolean saveData, List<FileSystem> fsToClose) throws IOException, URISyntaxException {
		List<RClass> ret = new ArrayList<RClass>();

		if (file.toString().endsWith(".class")) {
			ret.add(analyze(srcPath, Files.readAllBytes(file), saveData));
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
						ret.add(analyze(srcPath, Files.readAllBytes(file), saveData));
					}

					return FileVisitResult.CONTINUE;
				}
			});
		}

		return ret;
	}

	private RClass analyze(Path srcPath, byte[] data, boolean saveData) {
		final RClass ret = new RClass(srcPath, saveData ? data : null);

		ClassReader reader = new ClassReader(data);

		reader.accept(new ClassVisitor(Opcodes.ASM7) {
			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				ret.name = mapClass(name);
				ret.superName = mapClass(superName);
				ret.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
				ret.interfaces = new String[interfaces.length];
				for (int i = 0; i < interfaces.length; i++) ret.interfaces[i] = mapClass(interfaces[i]);
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
				ret.methods.put(name + desc, new Member(name, desc, access));

				return null;
			}

			@Override
			public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
				ret.fields.put(name + ";;" + desc, new Member(name, desc, access));

				return null;
			}
		}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE);

		return ret;
	}

	String mapClass(String className) {
		String ret = classMap.get(className);

		return ret != null ? ret : className;
	}

	private void merge() {
		for (RClass node : nodes.values()) {
			assert node.superName != null;

			RClass parent = nodes.get(node.superName);

			if (parent != null) {
				node.parents.add(parent);
				parent.children.add(node);
			}

			for (String iface : node.interfaces) {
				parent = nodes.get(iface);

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
		if (conflicts.isEmpty()) return;

		System.out.println("Mapping conflicts detected:");
		boolean unfixableConflicts = false;

		for (Map.Entry<TypedMember, Set<String>> entry : conflicts.entrySet()) {
			TypedMember tmember = entry.getKey();
			Member member = tmember.cls.getMember(tmember.type, tmember.id);
			String newName = (tmember.type == MemberType.METHOD ? tmember.cls.methodsToMap : tmember.cls.fieldsToMap).get(tmember.id);
			Set<String> names = entry.getValue();
			names.add(tmember.cls.name+"/"+newName);

			System.out.printf("  %s %s %s (%s) -> %s%n", tmember.cls.name, tmember.type.name(), member.name, member.desc, names);

			if (ignoreConflicts) {
				Map<String, String> mappings = tmember.type == MemberType.METHOD ? methodMap : fieldMap;
				String mappingName = mappings.get(tmember.cls.name+"/"+tmember.id);

				if (mappingName == null) { // no direct mapping match, try parents
					Queue<RClass> queue = new ArrayDeque<>(tmember.cls.parents);
					RClass cls;

					while ((cls = queue.poll()) != null) {
						mappingName = mappings.get(cls.name+"/"+tmember.id);
						if (mappingName != null) break;

						queue.addAll(cls.parents);
					}
				}

				if (mappingName == null) {
					unfixableConflicts = true;
				} else {
					mappingName = stripClassName(mappingName, tmember.type);
					ConcurrentMap<String, String> outputMap = (tmember.type == MemberType.METHOD) ? tmember.cls.methodsToMap : tmember.cls.fieldsToMap;
					outputMap.put(tmember.id, mappingName);
					System.out.println("    fixable: replaced with "+mappingName);
				}
			}
		}

		if (!ignoreConflicts || unfixableConflicts) {
			if (ignoreConflicts) System.out.println("There were unfixable conflicts.");

			System.exit(1);
		}
	}

	public void apply(Path srcPath, final BiConsumer<String, byte[]> outputConsumer) {
		if (dirty) {
			merge();
			propagate();

			dirty = false;
		}

		List<Future<?>> futures = new ArrayList<>();

		for (final RClass cls : nodes.values()) {
			if (cls.srcPath != srcPath) continue; // TODO: use a more elegant way to filter files to process (whether they were an input)

			futures.add(threadPool.submit(() -> outputConsumer.accept(cls.name, apply(cls))));
		}

		waitForAll(futures);
	}

	private byte[] apply(final RClass cls) {
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

		reader.accept(new AsmClassRemapper(visitor, remapper, renameInvalidLocals), flags);
		// TODO: compute frames (-Xverify:all -XX:-FailOverToOldVerifier)

		return writer.toByteArray();
	}

	private void waitForAll(Iterable<Future<?>> futures) {
		try {
			for (Future<?> future : futures) {
				future.get();
			}
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	String getClassName(String nameDesc, MemberType type) {
		int descStart = getDescStart(nameDesc, type);
		int nameStart = nameDesc.lastIndexOf('/', descStart - 1);
		if (nameStart == -1) nameStart = 0;

		return nameDesc.substring(0, nameStart);
	}

	String stripClassName(String nameDesc, MemberType type) {
		int descStart = getDescStart(nameDesc, type);
		int nameStart = nameDesc.lastIndexOf('/', descStart - 1);
		if (nameStart == -1) nameStart = 0;

		return nameDesc.substring(nameStart + 1);
	}

	String stripDesc(String nameDesc, MemberType type) {
		return nameDesc.substring(0, getDescStart(nameDesc, type));
	}

	private int getDescStart(String nameDesc, MemberType type) {
		int ret;

		if (type == MemberType.METHOD) {
			ret = nameDesc.indexOf('(');
		} else {
			ret = nameDesc.indexOf(";;");
		}

		if (ret == -1) ret = nameDesc.length();

		return ret;
	}

	enum Namespace {
		Unknown
	}

	class RClass {
		RClass(Path srcFile, byte[] data) {
			this.srcPath = srcFile;
			this.data = data;
		}

		Member getMember(MemberType type, String id) {
			if (type == MemberType.METHOD) {
				return methods.get(id);
			} else {
				return fields.get(id);
			}
		}

		/**
		 * Rename the member src to dst and continue propagating in dir.
		 *
		 * @param type Member type.
		 * @param idSrc Existing name.
		 * @param idDst New name.
		 * @param dir Futher propagation direction.
		 */
		void propagate(MemberType type, String originatingCls, String idSrc, String idDst, Direction dir, boolean isVirtual, boolean first, Set<RClass> visitedUp, Set<RClass> visitedDown) {
			/*
			 * initial private member or static method in interface: only local
			 * non-virtual: up to matching member (if not already in this), then down until matching again (exclusive)
			 * virtual: all across the hierarchy, only non-private|static can change direction - skip private|static in interfaces
			 */

			Member member = getMember(type, idSrc);

			if (member != null) {
				if (!first && !isVirtual) { // down propagation from non-virtual (static) member matching the signature again, which starts its own namespace
					return;
				}

				if (first // directly mapped
						|| (member.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0 // not private and not static
						|| propagatePrivate
						|| !forcePropagation.isEmpty() && forcePropagation.contains(name.replace('/', '.')+"."+member.name)) { // don't rename private members unless forced or initial (=dir any)
					ConcurrentMap<String, String> outputMap = (type == MemberType.METHOD) ? methodsToMap : fieldsToMap;
					String prev = outputMap.putIfAbsent(idSrc, idDst);

					if (prev != null && !prev.equals(idDst)) {
						conflicts.computeIfAbsent(new TypedMember(this, type, idSrc), x -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(originatingCls+"/"+idDst);
					}
					//System.out.printf("%s: %s %s -> %s\n", name, (type == Type.METHOD) ? "Method" : "Field", idSrc, idDst);
				}

				if (first
						&& ((member.access & Opcodes.ACC_PRIVATE) != 0 // private members don't propagate, but they may get skipped over by overriding virtual methods
						|| type == MemberType.METHOD && isInterface && !isVirtual)) { // non-virtual interface methods don't propagate either, the jvm only resolves direct accesses to them
					return;
				}
			} else { // member == null
				assert !first && (type == MemberType.FIELD || !isInterface || isVirtual);

				// Java likes/allows to access members in a super class by querying the "this"
				// class directly. To cover this, outputMap is being populated regardless.

				ConcurrentMap<String, String> outputMap = (type == MemberType.METHOD) ? methodsToMap : fieldsToMap;
				outputMap.putIfAbsent(idSrc, idDst);
			}

			assert isVirtual || dir == Direction.DOWN;

			/*
			 * Propagate the mapping along the hierarchy tree.
			 *
			 * The mapping ensures that overriding and shadowing behaviors remains the same.
			 *
			 * Direction.ANY is from where the current element was the initial node as specified
			 * in the mappings. The member == null + dir checks above already verified that the
			 * member exists in the current node.
			 *
			 * Direction.UP/DOWN handle propagation skipping across nodes which don't contain the
			 * specific member, thus having no direct reference.
			 *
			 * isVirtual && ... handles propagation to an existing matching virtual member, which
			 * spawns a new initial node from the propagation perspective. This is necessary as
			 * different branches of the hierarchy tree that were not visited before may access it.
			 */

			if (dir == Direction.ANY || dir == Direction.UP || isVirtual && member != null && (member.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0) {
				for (RClass node : parents) {
					if (visitedUp.add(node)) {
						node.propagate(type, originatingCls, idSrc, idDst, Direction.UP, isVirtual, false, visitedUp, visitedDown);
					}
				}
			}

			if (dir == Direction.ANY || dir == Direction.DOWN || isVirtual && member != null && (member.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0) {
				for (RClass node : children) {
					if (visitedDown.add(node)) {
						node.propagate(type, originatingCls, idSrc, idDst, Direction.DOWN, isVirtual, false, visitedUp, visitedDown);
					}
				}
			}
		}

		RClass resolve(MemberType type, String id, Set<RClass> visited, Queue<RClass> queue) {
			Member member = getMember(type, id);
			if (member != null) return this;

			queue.add(this);
			visited.add(this);
			RClass cls;

			// step 1
			// method: search in all super classes recursively
			// field: search in all direct super interfaces recursively

			while ((cls = queue.poll()) != null) {
				for (RClass parent : cls.parents) {
					if (parent.isInterface == (type == MemberType.FIELD) && visited.add(parent)) {
						if (parent.getMember(type, id) != null) return parent;

						queue.add(parent);
					}
				}
			}

			visited.clear();
			queue.add(this);
			visited.add(this);
			RClass secondaryMatch = null;

			// step 2
			// method: search for non-static, non-private, non-abstract in all super interfaces recursively
			//         (breadth first search to obtain the potentially maximally-specific superinterface directly)
			// field: search in all super classes recursively
			// step 3
			// method: search for non-static, non-private in all super interfaces recursively

			// step 3 is a super set of step 2 with any option being able to be "arbitrarily chosen" as per the jvm
			// spec, so step 2 ignoring the "exactly one" match requirement doesn't matter and >potentially<
			// maximally-specific superinterface is good enough

			while ((cls = queue.poll()) != null) {
				for (RClass parent : cls.parents) {
					if (parent.isInterface != (type == MemberType.FIELD) && visited.add(parent)) {
						Member parentMember = parent.getMember(type, id);

						if (parentMember != null
								&& (type == MemberType.FIELD || (parentMember.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0)) {
							if (type == MemberType.METHOD && (parentMember.access & (Opcodes.ACC_ABSTRACT)) != 0) {
								secondaryMatch = parent;
							} else {
								return parent;
							}
						}

						queue.add(parent);
					}
				}
			}

			return secondaryMatch;
		}

		@Override
		public String toString() {
			return name;
		}

		private final Path srcPath;
		private final byte[] data;
		private final Map<String, Member> methods = new HashMap<String, Member>();
		private final Map<String, Member> fields = new HashMap<String, Member>();
		private final Set<RClass> parents = new HashSet<RClass>();
		private final Set<RClass> children = new HashSet<RClass>();
		final ConcurrentMap<String, String> methodsToMap = new ConcurrentHashMap<String, String>();
		final ConcurrentMap<String, String> fieldsToMap = new ConcurrentHashMap<String, String>();
		String name;
		String superName;
		boolean isInterface;
		String[] interfaces;
	}

	class Member {
		Member(String name, String desc, int access) {
			this.name = name;
			this.desc = desc;
			this.access = access;
		}

		String name;
		String desc;
		int access;
	}

	enum Direction {
		ANY,
		UP,
		DOWN
	}

	enum MemberType {
		METHOD,
		FIELD
	}

	class TypedMember {
		public TypedMember(RClass cls, MemberType type, String id) {
			this.cls = cls;
			this.type = type;
			this.id = id;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof TypedMember)) return false;

			TypedMember o = (TypedMember) obj;

			return cls == o.cls &&
					type == o.type &&
					id.equals(o.id);
		}

		@Override
		public int hashCode() {
			return (cls.hashCode() * 31 + type.hashCode()) * 31 + id.hashCode();
		}

		final RClass cls;
		final MemberType type;
		final String id;
	}

	class Propagation implements Runnable {
		Propagation(MemberType type, List<Map.Entry<String, String> > tasks) {
			this.type = type;
			this.tasks.addAll(tasks);
		}

		@Override
		public void run() {
			Set<RClass> visitedUp = Collections.newSetFromMap(new IdentityHashMap<>());
			Set<RClass> visitedDown = Collections.newSetFromMap(new IdentityHashMap<>());
			Queue<RClass> queue = new ArrayDeque<>();

			for (Map.Entry<String, String> entry : tasks) {
				String className = getClassName(entry.getValue(), type);
				RClass node = nodes.get(className);
				if (node == null) continue; // not available for this Side

				String idSrc = stripClassName(entry.getKey(), type);
				String idDst = stripClassName(entry.getValue(), type);
				if (idSrc.equals(idDst)) continue; // no name change

				Member member = node.getMember(type, idSrc);

				if (member == null) {
					if (resolveMissing) {
						// resolve real owner in case the mapping doesn't reference the actual class
						node = node.resolve(type, idSrc, visitedUp, queue);
						visitedUp.clear();
						queue.clear();

						// at this point node is null or the member must exist in node

						if (node == null) {
							// not available for this Side
							//System.out.println("Unknown "+(type == MemberType.METHOD ? "method" : "field")+" referenced: "+entry.getKey());
							continue;
						} else {
							member = node.getMember(type, idSrc);
							assert member != null;
						}
					} else {
						// not available for this Side
						continue;
					}
				}

				boolean isVirtual = type == MemberType.METHOD && (member.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0;

				visitedUp.add(node);
				visitedDown.add(node);
				node.propagate(type, className, idSrc, idDst, isVirtual ? Direction.ANY : Direction.DOWN, isVirtual, true, visitedUp, visitedDown);
				visitedUp.clear();
				visitedDown.clear();
			}
		}

		private final MemberType type;
		private final List<Map.Entry<String, String> > tasks = new ArrayList<Map.Entry<String,String> >();
	}

	private final boolean check = false;

	private final Set<String> forcePropagation;
	private final boolean propagatePrivate;
	private final boolean removeFrames;
	private final boolean ignoreConflicts;
	private final boolean resolveMissing;
	private final boolean rebuildSourceFilenames;
	private final boolean renameInvalidLocals;
	final Map<String, String> classMap = new HashMap<>();
	final Map<String, String> methodMap = new HashMap<>();
	final Map<String, String> fieldMap = new HashMap<>();
	final Map<String, RClass> nodes = new HashMap<>();
	private final Map<TypedMember, Set<String>> conflicts = new ConcurrentHashMap<>();
	private final int threadCount;
	private final ExecutorService threadPool;
	private final AsmRemapper remapper = new AsmRemapper(this);

	private boolean dirty;
}
