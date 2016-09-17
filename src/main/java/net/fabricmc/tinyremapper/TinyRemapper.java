package net.fabricmc.tinyremapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.common.io.ByteStreams;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.util.CheckClassAdapter;

public class TinyRemapper {
	public static void main(String[] rawArgs) {
		List<String> args = new ArrayList<String>(rawArgs.length);
		boolean reverse = false;
		File forcePropagationFile = null;

		for (String arg : rawArgs) {
			if (arg.startsWith("--")) {
				int valueSepPos = arg.indexOf('=');

				String argKey = valueSepPos == -1 ? arg.substring(2) : arg.substring(2, valueSepPos);
				argKey = argKey.toLowerCase(Locale.US);

				switch (argKey) {
				case "reverse":
					reverse = true;
					break;
				case "forcepropagation":
					forcePropagationFile = new File(arg.substring(valueSepPos + 1));
					break;
				case "propagateprivate":
					propagatePrivate = true;
					break;
				case "removeFrames":
					removeFrames = true;
					break;
				default:
					System.out.println("invalid argument: "+arg+".");
					System.exit(1);
				}
			} else {
				args.add(arg);
			}
		}

		if (args.size() < 5) {
			System.out.println("usage: <input> <output> <mappings> <from> <to> [<classpath>]... [--reverse] [--forcePropagation=<file>] [--propagatePrivate]");
			System.exit(1);
		}

		Path input = Paths.get(args.get(0));
		if (!Files.isReadable(input)) {
			System.out.println("Can't read input file "+input+".");
			System.exit(1);
		}

		Path output = Paths.get(args.get(1));
		if (Files.exists(output) && !Files.isDirectory(output)) {
			System.out.println("The output path "+output+" exists, but is no directory.");
			System.exit(1);
		}

		Path mappings = Paths.get(args.get(2));
		if (!Files.isReadable(mappings) || Files.isDirectory(mappings)) {
			System.out.println("Can't read mappings file "+mappings+".");
			System.exit(1);
		}

		String fromM = args.get(3);
		String toM = args.get(4);

		Path[] classpath = new Path[args.size() - 5];

		for (int i = 0; i < classpath.length; i++) {
			classpath[i] = Paths.get(args.get(i + 3));
			if (!Files.isReadable(classpath[i])) {
				System.out.println("Can't read classpath file "+i+": "+classpath[i]+".");
				System.exit(1);
			}
		}

		if (forcePropagationFile != null) {
			if (!forcePropagationFile.canRead()) {
				System.out.println("Can't read forcePropagation file "+forcePropagationFile+".");
				System.exit(1);
			}

			try (BufferedReader reader = new BufferedReader(new FileReader(forcePropagationFile))) {
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

		process(input, output, mappings, fromM, toM, classpath, reverse);
	}

	private static void process(Path input, Path output, Path mappings, String fromM, String toM, Path[] classpath, boolean reverse) {
		long startTime = System.nanoTime();

		// class map

		try (BufferedReader reader = Files.newBufferedReader(mappings)) {
			TinyUtils.read(reader, fromM, toM, classMap, fieldMap, methodMap);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		System.out.printf("mappings: %d classes, %d methods, %d fields%n", classMap.size(), methodMap.size(), fieldMap.size());

		long classMapTime = System.nanoTime() - startTime;

		// read + analyze

		List<Future<List<RClass>>> futures = new ArrayList<>();
		futures.addAll(read(input, Namespace.Unknown, true));

		for (Path classpathFile : classpath) {
			futures.addAll(read(classpathFile, Namespace.Unknown, true));
		}

		try {
			for (Future<List<RClass> > future : futures) {
				for (RClass node : future.get()) {
					nodes.put(node.name, node);
				}
			}
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}

		// merge

		long mergeTime = System.nanoTime();
		merge();
		mergeTime = System.nanoTime() - mergeTime;

		// propagate

		long propagateTime = System.nanoTime();
		propagate();
		propagateTime = System.nanoTime() - propagateTime;

		// apply

		long applyTime = System.nanoTime();
		apply(input, output);
		applyTime = System.nanoTime() - applyTime;

		// finish

		threadPool.shutdown();
		try {
			threadPool.awaitTermination(20, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		System.out.printf("Finished after %.2f ms.\n", (System.nanoTime() - startTime) / 1e6);
		System.out.printf("classmap %.2f ms, read %.2f ms, analyze %.2f ms, merge %.2f ms, propagate %.2f ms, apply %.2f ms.\n", classMapTime / 1e6, readTime.get() / 1e6, analyzeTime.get() / 1e6, mergeTime / 1e6, propagateTime / 1e6, applyTime / 1e6);
	}

	private static List<Future<List<RClass>>> read(final Path file, final Namespace namespace, boolean saveData) {
		try {
			return read(file, namespace, file, saveData);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static List<Future<List<RClass>>> read(final Path file, final Namespace namespace, final Path srcPath, final boolean saveData) throws IOException {
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
								return readFile(file, namespace, srcPath, saveData);
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

	private static List<RClass> readFile(Path file, Namespace namespace, final Path srcPath, boolean saveData) throws IOException {
		List<RClass> ret = new ArrayList<RClass>();

		if (file.getFileName().endsWith(".class")) {
			long startTime = System.nanoTime();

			byte[] data = Files.readAllBytes(file);

			readTime.addAndGet(System.nanoTime() - startTime);

			ret.add(analyze(srcPath, data, saveData));
		} else {
			try (ZipFile zip = new ZipFile(file.toFile())) {
				for (Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements(); ) {
					ZipEntry entry = it.nextElement();

					if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
						long startTime = System.nanoTime();
						byte[] data;

						try (InputStream is = zip.getInputStream(entry)) {
							data = ByteStreams.toByteArray(is);
						}

						readTime.addAndGet(System.nanoTime() - startTime);
						ret.add(analyze(srcPath, data, saveData));
					}
				}
			}
		}

		return ret;
	}

	private static RClass analyze(Path srcPath, byte[] data, boolean saveData) {
		long startTime = System.nanoTime();
		final RClass ret = new RClass(srcPath, saveData ? data : null);

		ClassReader reader = new ClassReader(data);

		reader.accept(new ClassVisitor(Opcodes.ASM5) {
			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				ret.name = mapClass(name);
				ret.superName = mapClass(superName);
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

		analyzeTime.addAndGet(System.nanoTime() - startTime);

		return ret;
	}

	static String mapClass(String className) {
		String ret = classMap.get(className);

		return ret != null ? ret : className;
	}

	private static void merge() {
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

	static void propagate() {
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

	private static void handleConflicts() {
		if (conflicts.isEmpty()) return;

		System.out.println("Mapping conflicts detected:");

		for (Map.Entry<TypedMember, Set<String>> entry : conflicts.entrySet()) {
			TypedMember tmember = entry.getKey();
			Member member = (tmember.type == MemberType.METHOD ? tmember.cls.methods : tmember.cls.fields).get(tmember.id);
			String newName = (tmember.type == MemberType.METHOD ? tmember.cls.methodsToMap : tmember.cls.fieldsToMap).get(tmember.id);
			Set<String> names = entry.getValue();
			names.add(newName);

			System.out.printf("  %s %s %s (%s) -> %s%n", tmember.cls.name, tmember.type.name(), member.name, member.desc, names);
		}

		System.exit(1);
	}

	private static void apply(Path srcPath, final Path outputPath) {
		List<Future<?>> futures = new ArrayList<>();

		for (final RClass cls : nodes.values()) {
			if (cls.srcPath != srcPath) continue;

			futures.add(threadPool.submit(new Runnable() {
				@Override
				public void run() {
					apply(cls, outputPath);
				}
			}));
		}

		waitForAll(futures);
	}

	private static void apply(final RClass cls, Path outputPath) {
		ClassReader reader = new ClassReader(cls.data);
		ClassWriter writer = new ClassWriter(0);
		int flags = removeFrames ? ClassReader.SKIP_FRAMES : ClassReader.EXPAND_FRAMES;
		reader.accept(new ClassRemapper(check ? new CheckClassAdapter(writer) : writer, remapper), flags);
		// TODO: compute frames (-Xverify:all -XX:-FailOverToOldVerifier)

		int pkgEnd = cls.name.lastIndexOf('/');

		if (pkgEnd != -1) outputPath = outputPath.resolve(cls.name.substring(0, pkgEnd));
		Path output = outputPath.resolve(cls.name.substring(pkgEnd + 1)+".class");

		try {
			Files.createDirectories(outputPath);
			Files.write(output, writer.toByteArray());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
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

	private static final Remapper remapper = new Remapper() {
		@Override
		public String map(String typeName) {
			String ret = classMap.get(typeName);

			return ret != null ? ret : typeName;
		}

		@Override
		public String mapFieldName(String owner, String name, String desc) {
			/*String ret = fieldMap.get(owner+"/"+name);
            if (ret != null) return stripClassName(ret);*/

			RClass cls = nodes.get(owner);

			if (cls == null) {
				owner = classMap.get(owner);
				if (owner == null) return name;

				cls = nodes.get(owner);
				if (cls == null) return name;
			}

			String ret = cls.fieldsToMap.get(name + ";;" + desc);
			if (ret != null) return ret;

			assert fieldMap.get(owner+"/"+name+";;"+desc) == null;

			return name;
		}

		@Override
		public String mapMethodName(String owner, String name, String desc) {
			/*String ret = methodMap.get(owner+"/"+name+desc);
            if (ret != null) return stripDesc(stripClassName(ret));*/

			RClass cls = nodes.get(owner);

			if (cls == null) {
				owner = classMap.get(owner);
				if (owner == null) return name;

				cls = nodes.get(owner);
				if (cls == null) return name;
			}

			String ret = cls.methodsToMap.get(name+desc);
			if (ret != null) return ret;

			assert methodMap.get(owner+"/"+name+desc) == null;
			return name;
		}

		@Override
		public String mapInvokeDynamicMethodName(String name, String desc) {
			String owner = Type.getType(desc).getReturnType().getClassName();
			RClass cls = nodes.get(owner);

			if (cls == null) {
				owner = classMap.get(owner);
				if (owner == null) return name;

				cls = nodes.get(owner);
				if (cls == null) return name;
			}

			for (Map.Entry<String, String> entry : cls.methodsToMap.entrySet()) {
				String src = entry.getKey();
				int descStart = src.indexOf('/');

				if (name.length() == descStart && name.equals(src.substring(0, descStart))) {
					String dst = entry.getValue();

					return dst.substring(0, dst.indexOf('/'));
				}
			}

			return name;
		}
	};

	static String getClassName(String nameDesc, MemberType type) {
		int descStart = getDescStart(nameDesc, type);
		int nameStart = nameDesc.lastIndexOf('/', descStart - 1);
		if (nameStart == -1) nameStart = 0;

		return nameDesc.substring(0, nameStart);
	}

	static String stripClassName(String nameDesc, MemberType type) {
		int descStart = getDescStart(nameDesc, type);
		int nameStart = nameDesc.lastIndexOf('/', descStart - 1);
		if (nameStart == -1) nameStart = 0;

		return nameDesc.substring(nameStart + 1);
	}

	static String stripDesc(String nameDesc, MemberType type) {
		return nameDesc.substring(0, getDescStart(nameDesc, type));
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

	static enum Namespace {
		Unknown
	}

	static class RClass {
		RClass(Path srcFile, byte[] data) {
			this.srcPath = srcFile;
			this.data = data;
		}

		/**
		 * Rename the member src to dst and continue propagating in dir.
		 *
		 * @param type Member type.
		 * @param idSrc Existing name.
		 * @param idDst New name.
		 * @param dir Futher propagation direction.
		 */
		void propagate(MemberType type, String idSrc, String idDst, Direction dir, Set<RClass> visited) {
			Map<String, Member> members = (type == MemberType.METHOD) ? methods : fields;
			Member member = members.get(idSrc);
			boolean isMapped;

			if (member == null) {
				isMapped = true;
				member = members.get(idDst);
			} else {
				assert !members.containsKey(idDst);
				isMapped = false;
			}

			if (member != null) {
				if (!propagatePrivate &&
						Modifier.isPrivate(member.access) &&
						!forcePropagation.contains(name.replace('/', '.')+"."+member.name)) {
					// private doesn't have any inheritance effect on other classes
					return;
				}

				if (!isMapped) { // there's a matching element and it wasn't mapped yet
					ConcurrentMap<String, String> outputMap = (type == MemberType.METHOD) ? methodsToMap : fieldsToMap;
					String prev = outputMap.putIfAbsent(idSrc, idDst);

					if (prev != null && !prev.equals(idDst)) {
						conflicts.computeIfAbsent(new TypedMember(this, type, idSrc), x -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(idDst);
					}
					//System.out.printf("%s: %s %s -> %s\n", name, (type == Type.METHOD) ? "Method" : "Field", idSrc, idDst);
				}
			}

			if (member == null) {
				if (dir == Direction.ANY) {
					/*
					 * A mapping is initially (=ANY) applied to this class, but no matching member
					 * was found. It's assumed to be safe to ignore.
					 */

					return;
				}

				if (dir == Direction.DOWN) {
					/*
					 * Java likes/allows to access members in a super class by querying the "this"
					 * class directly. To cover this, outputMap is being populated regardless.
					 */

					ConcurrentMap<String, String> outputMap = (type == MemberType.METHOD) ? methodsToMap : fieldsToMap;
					outputMap.putIfAbsent(idSrc, idDst);
				}
			}

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
			 * member != null handles propagation to an existing matching member, which spawns a
			 * new initial node from the propagation perspective. This is necessary as different
			 * branches of the hierarchy tree that were not visited before may access it.
			 */

			if (dir == Direction.ANY || dir == Direction.UP || member != null) {
				for (RClass node : parents) {
					if (visited.add(node)) {
						node.propagate(type, idSrc, idDst, Direction.UP, visited);
					}
				}
			}

			if (dir == Direction.ANY || dir == Direction.DOWN || member != null) {
				for (RClass node : children) {
					if (visited.add(node)) {
						node.propagate(type, idSrc, idDst, Direction.DOWN, visited);
					}
				}
			}
		}

		private final Path srcPath;
		private final byte[] data;
		private final Map<String, Member> methods = new HashMap<String, Member>();
		private final Map<String, Member> fields = new HashMap<String, Member>();
		private final Set<RClass> parents = new HashSet<RClass>();
		private final Set<RClass> children = new HashSet<RClass>();
		private final ConcurrentMap<String, String> methodsToMap = new ConcurrentHashMap<String, String>();
		private final ConcurrentMap<String, String> fieldsToMap = new ConcurrentHashMap<String, String>();
		String name;
		String superName;
		String[] interfaces;
	}

	static class Member {
		Member(String name, String desc, int access) {
			this.name = name;
			this.desc = desc;
			this.access = access;
		}

		String name;
		String desc;
		int access;
	}

	static enum Direction {
		ANY,
		UP,
		DOWN
	}

	static enum MemberType {
		METHOD,
		FIELD
	}

	static class TypedMember {
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

	static class Propagation implements Runnable {
		Propagation(MemberType type, List<Map.Entry<String, String> > tasks) {
			this.type = type;
			this.tasks.addAll(tasks);
		}

		@Override
		public void run() {
			Set<RClass> visited = new HashSet<>();

			for (Map.Entry<String, String> entry : tasks) {
				String className = getClassName(entry.getValue(), type);
				RClass node = nodes.get(className);
				if (node == null) continue; // not available for this Side

				String idSrc = stripClassName(entry.getKey(), type);
				String idDst = stripClassName(entry.getValue(), type);
				if (idSrc.equals(idDst)) continue;

				visited.add(node);
				node.propagate(type, idSrc, idDst, Direction.ANY, visited);
				visited.clear();
			}
		}

		private final MemberType type;
		private final List<Map.Entry<String, String> > tasks = new ArrayList<Map.Entry<String,String> >();
	}

	private static final boolean check = false;

	private static final Set<String> forcePropagation = new HashSet<>();
	private static boolean propagatePrivate = false;
	private static boolean removeFrames = false;
	private static final Map<String, String> classMap = new HashMap<>();
	private static final Map<String, String> methodMap = new HashMap<>();
	private static final Map<String, String> fieldMap = new HashMap<>();
	private static final Map<String, RClass> nodes = new HashMap<>();
	private static final Map<TypedMember, Set<String>> conflicts = new ConcurrentHashMap<>();
	private static final int threadCount = Math.max(Runtime.getRuntime().availableProcessors(), 2);
	private static final ExecutorService threadPool = Executors.newFixedThreadPool(threadCount);
	private static final AtomicLong readTime = new AtomicLong();
	private static final AtomicLong analyzeTime = new AtomicLong();
}
