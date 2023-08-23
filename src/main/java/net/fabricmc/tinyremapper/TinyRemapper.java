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

import java.io.IOException;
import java.net.URISyntaxException;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipError;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.util.CheckClassAdapter;

import net.fabricmc.tinyremapper.IMappingProvider.MappingAcceptor;
import net.fabricmc.tinyremapper.IMappingProvider.Member;
import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrEnvironment;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.api.TrMember.MemberType;

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

		/**
		 * Keep the input data after consuming it for apply(), allows multiple apply invocations() even without input tag use.
		 */
		public Builder keepInputData(boolean value) {
			this.keepInputData = value;
			return this;
		}

		public Builder withForcedPropagation(Set<String> entries) {
			forcePropagation.addAll(entries);
			return this;
		}

		public Builder withKnownIndyBsm(Set<String> entries) {
			knownIndyBsm.addAll(entries);
			return this;
		}

		public Builder propagatePrivate(boolean value) {
			propagatePrivate = value;
			return this;
		}

		public Builder propagateBridges(LinkedMethodPropagation value) {
			propagateBridges = value;
			return this;
		}

		public Builder propagateRecordComponents(LinkedMethodPropagation value) {
			propagateRecordComponents = value;
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

		public Builder skipLocalVariableMapping(boolean value) {
			skipLocalMapping = value;
			return this;
		}

		public Builder renameInvalidLocals(boolean value) {
			renameInvalidLocals = value;
			return this;
		}

		/**
		 * Pattern that flags matching local variable (and arg) names as invalid for the usual renameInvalidLocals processing.
		 */
		public Builder invalidLvNamePattern(Pattern value) {
			this.invalidLvNamePattern = value;
			return this;
		}

		/**
		 * Whether to copy lv names from other local variables if the original name was missing or invalid.
		 */
		public Builder inferNameFromSameLvIndex(boolean value) {
			this.inferNameFromSameLvIndex = value;
			return this;
		}

		@Deprecated
		public Builder extraAnalyzeVisitor(ClassVisitor visitor) {
			return extraAnalyzeVisitor((mrjVersion, className, next) -> {
				if (next != null) throw new UnsupportedOperationException("can't chain fixed instance analyze visitors");

				return visitor;
			});
		}

		public Builder extraAnalyzeVisitor(AnalyzeVisitorProvider provider) {
			analyzeVisitors.add(provider);
			return this;
		}

		public Builder extraStateProcessor(StateProcessor processor) {
			stateProcessors.add(processor);
			return this;
		}

		public Builder extraRemapper(Remapper remapper) {
			extraRemapper = remapper;
			return this;
		}

		public Builder extraPreApplyVisitor(ApplyVisitorProvider provider) {
			preApplyVisitors.add(provider);
			return this;
		}

		public Builder extraPostApplyVisitor(ApplyVisitorProvider provider) {
			this.postApplyVisitors.add(provider);
			return this;
		}

		public Builder extension(TinyRemapper.Extension extension) {
			extension.attach(this);
			return this;
		}

		public TinyRemapper build() {
			TinyRemapper remapper = new TinyRemapper(mappingProviders, ignoreFieldDesc, threadCount,
					keepInputData,
					forcePropagation, knownIndyBsm, propagatePrivate,
					propagateBridges, propagateRecordComponents,
					removeFrames, ignoreConflicts, resolveMissing, checkPackageAccess || fixPackageAccess, fixPackageAccess,
					rebuildSourceFilenames, skipLocalMapping, renameInvalidLocals, invalidLvNamePattern, inferNameFromSameLvIndex,
					analyzeVisitors, stateProcessors, preApplyVisitors, postApplyVisitors,
					extraRemapper);

			return remapper;
		}

		private final Set<IMappingProvider> mappingProviders = new HashSet<>();
		private boolean ignoreFieldDesc;
		private int threadCount;
		private final Set<String> forcePropagation = new HashSet<>();
		private final Set<String> knownIndyBsm = new HashSet<>();
		private boolean keepInputData = false;
		private boolean propagatePrivate = false;
		private LinkedMethodPropagation propagateBridges = LinkedMethodPropagation.DISABLED;
		private LinkedMethodPropagation propagateRecordComponents = LinkedMethodPropagation.DISABLED;
		private boolean removeFrames = false;
		private boolean ignoreConflicts = false;
		private boolean resolveMissing = false;
		private boolean checkPackageAccess = false;
		private boolean fixPackageAccess = false;
		private boolean rebuildSourceFilenames = false;
		private boolean skipLocalMapping = false;
		private boolean renameInvalidLocals = false;
		private Pattern invalidLvNamePattern;
		private boolean inferNameFromSameLvIndex;
		private final List<AnalyzeVisitorProvider> analyzeVisitors = new ArrayList<>();
		private final List<StateProcessor> stateProcessors = new ArrayList<>();
		private final List<ApplyVisitorProvider> preApplyVisitors = new ArrayList<>();
		private final List<ApplyVisitorProvider> postApplyVisitors = new ArrayList<>();
		private Remapper extraRemapper;
	}

	public interface Extension {
		void attach(TinyRemapper.Builder builder);
	}

	public interface AnalyzeVisitorProvider {
		ClassVisitor insertAnalyzeVisitor(int mrjVersion, String className, ClassVisitor next);
	}

	public interface StateProcessor {
		void process(TrEnvironment env);
	}

	public interface ApplyVisitorProvider {
		ClassVisitor insertApplyVisitor(TrClass cls, ClassVisitor next);
	}

	private TinyRemapper(Collection<IMappingProvider> mappingProviders, boolean ignoreFieldDesc,
			int threadCount,
			boolean keepInputData,
			Set<String> forcePropagation, Set<String> knownIndyBsm, boolean propagatePrivate,
			LinkedMethodPropagation propagateBridges, LinkedMethodPropagation propagateRecordComponents,
			boolean removeFrames,
			boolean ignoreConflicts,
			boolean resolveMissing,
			boolean checkPackageAccess,
			boolean fixPackageAccess,
			boolean rebuildSourceFilenames,
			boolean skipLocalMapping,
			boolean renameInvalidLocals, Pattern invalidLvNamePattern, boolean inferNameFromSameLvIndex,
			List<AnalyzeVisitorProvider> analyzeVisitors, List<StateProcessor> stateProcessors,
			List<ApplyVisitorProvider> preApplyVisitors, List<ApplyVisitorProvider> postApplyVisitors,
			Remapper extraRemapper) {
		this.mappingProviders = mappingProviders;
		this.ignoreFieldDesc = ignoreFieldDesc;
		this.threadCount = threadCount > 0 ? threadCount : Math.max(Runtime.getRuntime().availableProcessors(), 2);
		this.keepInputData = keepInputData;
		this.threadPool = Executors.newFixedThreadPool(this.threadCount);
		this.forcePropagation = forcePropagation;
		this.knownIndyBsm = knownIndyBsm;
		this.propagatePrivate = propagatePrivate;
		this.propagateBridges = propagateBridges;
		this.propagateRecordComponents = propagateRecordComponents;
		this.removeFrames = removeFrames;
		this.ignoreConflicts = ignoreConflicts;
		this.resolveMissing = resolveMissing;
		this.checkPackageAccess = checkPackageAccess;
		this.fixPackageAccess = fixPackageAccess;
		this.rebuildSourceFilenames = rebuildSourceFilenames;
		this.skipLocalMapping = skipLocalMapping;
		this.renameInvalidLocals = renameInvalidLocals;
		this.invalidLvNamePattern = invalidLvNamePattern;
		this.inferNameFromSameLvIndex = inferNameFromSameLvIndex;
		this.analyzeVisitors = analyzeVisitors;
		this.stateProcessors = stateProcessors;
		this.preApplyVisitors = preApplyVisitors;
		this.postApplyVisitors = postApplyVisitors;
		this.extraRemapper = extraRemapper;

		this.knownIndyBsm.add("java/lang/invoke/StringConcatFactory");
		this.knownIndyBsm.add("java/lang/runtime/ObjectMethods");
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

		outputBuffer = null;
		defaultState.classes.clear();
		mrjStates.clear();
	}

	public InputTag createInputTag() {
		InputTag ret = new InputTag();
		InputTag[] array = { ret };

		Map<InputTag, InputTag[]> oldTags, newTags;

		do { // cas loop
			oldTags = this.singleInputTags.get();
			newTags = new IdentityHashMap<>(oldTags.size() + 1);
			newTags.putAll(oldTags);
			newTags.put(ret, array);
		} while (!singleInputTags.compareAndSet(oldTags, newTags));

		return ret;
	}

	public void readInputs(final Path... inputs) {
		readInputs(null, inputs);
	}

	public void readInputs(InputTag tag, Path... inputs) {
		read(inputs, true, tag).join();
	}

	public CompletableFuture<?> readInputsAsync(Path... inputs) {
		return readInputsAsync(null, inputs);
	}

	public CompletableFuture<?> readInputsAsync(InputTag tag, Path... inputs) {
		CompletableFuture<?> ret = read(inputs, true, tag);

		if (!ret.isDone()) {
			pendingReads.add(ret);
		} else {
			ret.join();
		}

		return ret;
	}

	public void readClassPath(final Path... inputs) {
		read(inputs, false, null).join();
	}

	public CompletableFuture<?> readClassPathAsync(final Path... inputs) {
		CompletableFuture<?> ret = read(inputs, false, null);

		if (!ret.isDone()) {
			pendingReads.add(ret);
		} else {
			ret.join();
		}

		return ret;
	}

	private CompletableFuture<List<ClassInstance>> read(Path[] inputs, boolean isInput, InputTag tag) {
		InputTag[] tags = singleInputTags.get().get(tag);
		List<CompletableFuture<List<ClassInstance>>> futures = new ArrayList<>();
		List<FileSystemReference> fsToClose = Collections.synchronizedList(new ArrayList<>());

		for (Path input : inputs) {
			futures.addAll(read(input, isInput, tags, true, fsToClose));
		}

		CompletableFuture<List<ClassInstance>> ret;

		if (futures.isEmpty()) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		} else if (futures.size() == 1) {
			ret = futures.get(0);
		} else {
			ret = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
					.thenApply(ignore -> futures.stream().flatMap(f -> f.join().stream()).collect(Collectors.toList()));
		}

		if (!dirty) {
			dirty = true;

			for (MrjState state : mrjStates.values()) {
				state.dirty = true;
			}
		}

		return ret.whenComplete((res, exc) -> {
			for (FileSystemReference fs : fsToClose) {
				try {
					fs.close();
				} catch (IOException e) {
					// ignore
				}
			}

			if (res != null) {
				for (ClassInstance node : res) {
					addClass(node, readClasses, true);
				}
			}

			assert dirty;
		});
	}

	private static void addClass(ClassInstance cls, Map<String, ClassInstance> out, boolean isVersionAware) {
		// two different MRJ version will not cause warning if isVersionAware is true
		String name = isVersionAware ? ClassInstance.getMrjName(cls.getName(), cls.getMrjVersion()) : cls.getName();

		// add new class or replace non-input class with input class, warn if two input classes clash
		for (;;) {
			ClassInstance prev = out.putIfAbsent(name, cls);
			if (prev == null) return;

			if (prev.isMrjCopy() && prev.getMrjVersion() < cls.getMrjVersion()) {
				// if {@code prev} is MRJ copy and {@code prev}'s origin version is less than {@code cls}'s
				// origin version, then we should update the class.
				if (out.replace(name, prev, cls)) {
					return;
				} else {
					// loop
				}
			} else if (cls.isInput) {
				if (prev.isInput) {
					System.out.printf("duplicate input class %s, from %s and %s%n", name, prev.srcPath, cls.srcPath);
					prev.addInputTags(cls.getInputTags());
					return;
				} else if (out.replace(name, prev, cls)) { // cas with retry-loop on failure
					cls.addInputTags(prev.getInputTags());
					return;
				} else {
					// loop
				}
			} else {
				prev.addInputTags(cls.getInputTags());
				return;
			}
		}
	}

	private List<CompletableFuture<List<ClassInstance>>> read(final Path file, boolean isInput, InputTag[] tags,
			boolean saveData, final List<FileSystemReference> fsToClose) {
		try {
			return read(file, isInput, tags, file, saveData, fsToClose);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private List<CompletableFuture<List<ClassInstance>>> read(final Path file, boolean isInput, InputTag[] tags, final Path srcPath,
			final boolean saveData, final List<FileSystemReference> fsToClose) throws IOException {
		List<CompletableFuture<List<ClassInstance>>> ret = new ArrayList<>();

		Files.walkFileTree(file, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				String name = file.getFileName().toString();

				if (name.endsWith(".jar")
						|| name.endsWith(".zip")
						|| name.endsWith(".class")) {
					ret.add(CompletableFuture.supplyAsync(new Supplier<List<ClassInstance>>() {
						@Override
						public List<ClassInstance> get() {
							try {
								return readFile(file, isInput, tags, srcPath, fsToClose);
							} catch (URISyntaxException e) {
								throw new RuntimeException(e);
							} catch (IOException | ZipError e) {
								throw new RuntimeException("Error reading file "+file, e);
							}
						}
					}, threadPool));
				}

				return FileVisitResult.CONTINUE;
			}
		});

		return ret;
	}

	private List<ClassInstance> readFile(Path file, boolean isInput, InputTag[] tags, final Path srcPath,
			List<FileSystemReference> fsToClose) throws IOException, URISyntaxException {
		List<ClassInstance> ret = new ArrayList<ClassInstance>();

		if (file.toString().endsWith(".class")) {
			ClassInstance res = analyze(isInput, tags, srcPath, file);
			if (res != null) ret.add(res);
		} else {
			FileSystemReference fs = FileSystemReference.openJar(file);
			fsToClose.add(fs);

			Files.walkFileTree(fs.getPath("/"), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.toString().endsWith(".class")) {
						ClassInstance res = analyze(isInput, tags, srcPath, file);
						if (res != null) ret.add(res);
					}

					return FileVisitResult.CONTINUE;
				}
			});
		}

		return ret;
	}

	/**
	 * Determine the MRJ version of the supplied class file and name.
	 *
	 * <p>This assumes that the file path follows the usual META-INF/versions/{@code <version>}/pkg/for/cls.class form.
	 */
	private static int analyzeMrjVersion(Path file, String name) {
		assert file.getFileName().toString().endsWith(".class");

		int pkgCount = 0;
		int pos = 0;

		while ((pos = name.indexOf('/', pos) + 1) > 0) {
			pkgCount++;
		}

		int pathNameCount = file.getNameCount();
		int pathNameOffset = pathNameCount - pkgCount - 1; // path index for root package

		if (pathNameOffset >= 3
				&& file.getName(pathNameOffset - 3).toString().equals("META-INF") // root pkg is in META-INF/x/x
				&& file.getName(pathNameOffset - 2).toString().equals("versions") // root pkg is in META-INF/versions/x
				&& file.subpath(pathNameOffset, pathNameCount).toString().replace('\\', '/').regionMatches(0, name, 0, name.length())) { // verify class name == path from root pkg dir, ignores suffix like .class
			try {
				return Integer.parseInt(file.getName(pathNameOffset - 1).toString());
			} catch (NumberFormatException e) {
				// ignore
			}
		}

		return ClassInstance.MRJ_DEFAULT;
	}

	private ClassInstance analyze(boolean isInput, InputTag[] tags, Path srcPath, Path file) throws IOException {
		byte[] data = Files.readAllBytes(file);
		ClassReader reader;

		try {
			reader = new ClassReader(data);
		} catch (Throwable t) {
			throw new RuntimeException("error analyzing "+file+" from "+srcPath, t);
		}

		if ((reader.getAccess() & Opcodes.ACC_MODULE) != 0) return null; // special attribute for module-info.class, can't be a regular class

		final String name = reader.getClassName();
		final int mrjVersion = analyzeMrjVersion(file, name);

		final ClassInstance ret = new ClassInstance(this, isInput, tags, srcPath, isInput ? data : null);

		ClassVisitor cv = new ClassVisitor(Opcodes.ASM9) {
			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				ret.init(name, version, mrjVersion, signature, superName, access, interfaces);
				super.visit(version, access, name, signature, superName, interfaces);
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
				MemberInstance prev = ret.addMember(new MemberInstance(TrMember.MemberType.METHOD, ret, name, desc, access, ret.getMembers().size()));
				if (prev != null) throw new RuntimeException(String.format("duplicate method %s/%s%s in inputs", ret.getName(), name, desc));

				return super.visitMethod(access, name, desc, signature, exceptions);
			}

			@Override
			public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
				MemberInstance prev = ret.addMember(new MemberInstance(TrMember.MemberType.FIELD, ret, name, desc, access, ret.getMembers().size()));
				if (prev != null) throw new RuntimeException(String.format("duplicate field %s/%s;;%s in inputs", ret.getName(), name, desc));

				return super.visitField(access, name, desc, signature, value);
			}
		};

		for (int i = analyzeVisitors.size() - 1; i >= 0; i--) {
			cv = analyzeVisitors.get(i).insertAnalyzeVisitor(mrjVersion, name, cv);
		}

		reader.accept(cv, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE);

		return ret;
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
		// determine classes that map to the same target name, if there are any print duplicates and throw
		Set<String> testSet = new HashSet<>(classMap.values());

		if (testSet.size() != classMap.size()) { // src->target is not a 1:1 mapping
			Set<String> duplicates = new HashSet<>();

			for (String name : classMap.values()) {
				if (!testSet.remove(name)) {
					duplicates.add(name);
				}
			}

			System.out.println("non-unique class target name mappings:");

			for (String target : duplicates) {
				System.out.print("  [");
				boolean first = true;

				for (Map.Entry<String, String> e : classMap.entrySet()) {
					if (e.getValue().equals(target)) {
						if (first) {
							first = false;
						} else {
							System.out.print(", ");
						}

						System.out.print(e.getKey());
					}
				}

				System.out.printf("] -> %s%n", target);
			}

			throw new RuntimeException("duplicate class target name mappings detected");
		}
	}

	private void merge(MrjState state) {
		for (ClassInstance node : state.classes.values()) {
			assert node.getSuperName() != null;

			ClassInstance parent = state.getClass(node.getSuperName());

			if (parent != null) {
				node.parents.add(parent);
				parent.children.add(node);
			}

			for (String iface : node.getInterfaceNames0()) {
				parent = state.getClass(iface);

				if (parent != null) {
					node.parents.add(parent);
					parent.children.add(node);
				}
			}
		}
	}

	private void propagate(MrjState state) {
		List<Future<?>> futures = new ArrayList<>();
		List<Map.Entry<String, String>> tasks = new ArrayList<>();
		int maxTasks = methodMap.size() / threadCount / 4;

		for (Map.Entry<String, String> entry : methodMap.entrySet()) {
			tasks.add(entry);

			if (tasks.size() >= maxTasks) {
				futures.add(threadPool.submit(new Propagation(state, TrMember.MemberType.METHOD, tasks)));
				tasks.clear();
			}
		}

		futures.add(threadPool.submit(new Propagation(state, TrMember.MemberType.METHOD, tasks)));
		tasks.clear();

		for (Map.Entry<String, String> entry : fieldMap.entrySet()) {
			tasks.add(entry);

			if (tasks.size() >= maxTasks) {
				futures.add(threadPool.submit(new Propagation(state, TrMember.MemberType.FIELD, tasks)));
				tasks.clear();
			}
		}

		futures.add(threadPool.submit(new Propagation(state, TrMember.MemberType.FIELD, tasks)));
		tasks.clear();

		waitForAll(futures);

		handleConflicts(state);
	}

	private void handleConflicts(MrjState state) {
		Set<String> testSet = new HashSet<>();
		boolean targetNameCheckFailed = false;

		for (ClassInstance cls : state.classes.values()) {
			for (MemberInstance member : cls.getMembers()) {
				String name = member.getNewMappedName();
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
					String name = member.getNewMappedName();
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
				String newName = member.getNewMappedName();
				Set<String> names = entry.getValue();
				names.add(member.cls.getName()+"/"+newName);

				System.out.printf("  %s %s %s (%s) -> %s%n", member.cls.getName(), member.type.name(), member.name, member.desc, names);

				if (ignoreConflicts) {
					Map<String, String> mappings = member.type == TrMember.MemberType.METHOD ? methodMap : fieldMap;
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

			throw new RuntimeException("Unfixable conflicts");
		}
	}

	public void apply(final BiConsumer<String, byte[]> outputConsumer) {
		apply(outputConsumer, (InputTag[]) null);
	}

	public void apply(final BiConsumer<String, byte[]> outputConsumer, InputTag... inputTags) {
		// We expect apply() to be invoked only once if the user didn't request any input tags. Invoking it multiple
		// times still works with keepInputData=true, but wastes some time by redoing most processing.
		// With input tags the first apply invocation computes the entire output, but yields only what matches the given
		// input tags. The output data is being kept for eventual further apply() outputs, only finish() clears it.
		boolean hasInputTags = !singleInputTags.get().isEmpty();

		synchronized (this) { // guard against concurrent apply invocations
			refresh();

			if (outputBuffer == null) { // first (inputTags present) or full (no input tags) output invocation, process everything but don't output if input tags are present
				BiConsumer<ClassInstance, byte[]> immediateOutputConsumer;

				if (fixPackageAccess || hasInputTags) { // need re-processing or output buffering for repeated applies
					outputBuffer = new ConcurrentHashMap<>();
					immediateOutputConsumer = outputBuffer::put;
				} else {
					immediateOutputConsumer = (cls, data) -> outputConsumer.accept(ClassInstance.getMrjName(cls.getContext().remapper.map(cls.getName()), cls.getMrjVersion()), data);
				}

				List<Future<?>> futures = new ArrayList<>();

				for (MrjState state : mrjStates.values()) {
					mrjRefresh(state);

					for (final ClassInstance cls : state.classes.values()) {
						if (!cls.isInput) continue;

						if (cls.data == null) {
							if (!hasInputTags && !keepInputData) throw new IllegalStateException("invoking apply multiple times without input tags or hasInputData");
							throw new IllegalStateException("data for input class " + cls + " is missing?!");
						}

						futures.add(threadPool.submit(() -> immediateOutputConsumer.accept(cls, apply(cls))));
					}
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

						if (hasInputTags) {
							entry.setValue(data);
						} else {
							outputConsumer.accept(ClassInstance.getMrjName(cls.getContext().remapper.map(cls.getName()), cls.getMrjVersion()), data);
						}
					}

					if (!hasInputTags) outputBuffer = null; // don't expect repeat invocations

					classesToMakePublic.clear();
					membersToMakePublic.clear();
				} else if (needsFixes) {
					throw new RuntimeException(String.format("%d classes and %d members need access fixes", classesToMakePublic.size(), membersToMakePublic.size()));
				}
			}

			assert hasInputTags == (outputBuffer != null);

			if (outputBuffer != null) { // partial output selected by input tags
				for (Map.Entry<ClassInstance, byte[]> entry : outputBuffer.entrySet()) {
					ClassInstance cls = entry.getKey();

					if (inputTags == null || cls.hasAnyInputTag(inputTags)) {
						outputConsumer.accept(ClassInstance.getMrjName(cls.getContext().remapper.map(cls.getName()), cls.getMrjVersion()), entry.getValue());
					}
				}
			}
		}
	}

	/**
	 * This function will setup {@code mrjClasses} with any new MRJ version
	 * added. It will put the result of {@code constructMrjCopy} from lower
	 * MRJ version to the new version.
	 * @param newVersions the new versions that need to be added in to {@code mrjClasses}
	 */
	private void fixMrjClasses(Set<Integer> newVersions) {
		// ensure the new version is added from lowest to highest
		for (int newVersion: newVersions.stream().sorted().collect(Collectors.toList())) {
			MrjState newState = new MrjState(this, newVersion);

			if (mrjStates.put(newVersion, newState) != null) {
				throw new RuntimeException("internal error: duplicate versions in mrjClasses");
			}

			// find the fromVersion that just lower the the toVersion
			Optional<Integer> fromVersion = mrjStates.keySet().stream()
					.filter(v -> v < newVersion).max(Integer::compare);

			if (fromVersion.isPresent()) {
				Map<String, ClassInstance> fromClasses = mrjStates.get(fromVersion.get()).classes;

				for (ClassInstance cls: fromClasses.values()) {
					addClass(cls.constructMrjCopy(newState), newState.classes, false);
				}
			}
		}
	}

	private void refresh() {
		if (!dirty) {
			assert pendingReads.isEmpty();
			assert readClasses.isEmpty();

			return;
		}

		outputBuffer = null;

		if (!pendingReads.isEmpty()) {
			for (CompletableFuture<?> future : pendingReads) {
				future.join();
			}

			pendingReads.clear();
		}

		if (!readClasses.isEmpty()) {
			// fix any new adding MRJ versions
			Set<Integer> versions = readClasses.values().stream().map(ClassInstance::getMrjVersion).collect(Collectors.toSet());
			versions.removeAll(mrjStates.keySet());
			fixMrjClasses(versions);

			for (ClassInstance cls : readClasses.values()) {
				// TODO: this might be able to optimize, any suggestion?
				int clsVersion = cls.getMrjVersion();
				MrjState state = mrjStates.get(clsVersion);
				cls.setContext(state);
				addClass(cls, state.classes, false);

				for (int version: mrjStates.keySet()) {
					if (version > clsVersion) {
						MrjState newState = mrjStates.get(version);
						addClass(cls.constructMrjCopy(newState), newState.classes, false);
					}
				}
			}

			readClasses.clear();
		}

		loadMappings();
		checkClassMappings();

		assert dirty;
		dirty = false;
	}

	private void mrjRefresh(MrjState state) {
		if (!state.dirty) {
			return;
		}

		assert new HashSet<>(state.classes.values()).size() == state.classes.size();
		assert state.classes.values().stream().map(ClassInstance::getName).distinct().count() == state.classes.size();

		merge(state);
		propagate(state);

		for (StateProcessor processor : stateProcessors) {
			processor.process(state);
		}

		state.dirty = false;
	}

	private byte[] apply(final ClassInstance cls) {
		ClassReader reader = new ClassReader(cls.data);
		ClassWriter writer = new ClassWriter(0);
		int flags = removeFrames ? ClassReader.SKIP_FRAMES : ClassReader.EXPAND_FRAMES;

		ClassVisitor visitor = writer;

		if (check) {
			visitor = new CheckClassAdapter(visitor);
		}

		for (int i = postApplyVisitors.size() - 1; i >= 0; i--) {
			visitor = postApplyVisitors.get(i).insertApplyVisitor(cls, visitor);
		}

		visitor = new AsmClassRemapper(visitor, cls.getContext().remapper, rebuildSourceFilenames,
				checkPackageAccess, skipLocalMapping, renameInvalidLocals, invalidLvNamePattern, inferNameFromSameLvIndex);

		for (int i = preApplyVisitors.size() - 1; i >= 0; i--) {
			visitor = preApplyVisitors.get(i).insertApplyVisitor(cls, visitor);
		}

		reader.accept(visitor, flags);

		// TODO: compute frames (-Xverify:all -XX:-FailOverToOldVerifier)

		if (!keepInputData) cls.data = null;

		return writer.toByteArray();
	}

	private byte[] fixClass(ClassInstance cls, byte[] data) {
		boolean makeClsPublic = classesToMakePublic.contains(cls);
		Set<String> clsMembersToMakePublic = null;

		for (MemberInstance member : cls.getMembers()) {
			if (membersToMakePublic.contains(member)) {
				if (clsMembersToMakePublic == null) clsMembersToMakePublic = new HashSet<>();

				AsmRemapper remapper = cls.getContext().remapper;
				String mappedName, mappedDesc;

				if (member.type == TrMember.MemberType.FIELD) {
					mappedName = remapper.mapFieldName(cls, member.name, member.desc);
					mappedDesc = remapper.mapDesc(member.desc);
				} else {
					mappedName = remapper.mapMethodName(cls, member.name, member.desc);
					mappedDesc = remapper.mapMethodDesc(member.desc);
				}

				clsMembersToMakePublic.add(MemberInstance.getId(member.type, mappedName, mappedDesc, ignoreFieldDesc));
			}
		}

		if (!makeClsPublic && clsMembersToMakePublic == null) return data;

		final Set<String> finalClsMembersToMakePublic = clsMembersToMakePublic;

		ClassReader reader = new ClassReader(data);
		ClassWriter writer = new ClassWriter(0);

		reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
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

	public synchronized TrEnvironment getEnvironment() {
		refresh();
		mrjRefresh(defaultState);
		return defaultState;
	}

	/**
	 * @deprecated Use {@link #getEnvironment} and {@link TrEnvironment#getRemapper} instead.
	 */
	@Deprecated
	public AsmRemapper getRemapper() {
		return (AsmRemapper) getEnvironment().getRemapper();
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

		if (type == TrMember.MemberType.METHOD) {
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
		Propagation(MrjState state, MemberType type, List<Map.Entry<String, String>> tasks) {
			this.state = state;
			this.type = type;
			this.tasks.addAll(tasks);
		}

		@Override
		public void run() {
			Set<ClassInstance> visitedUp = Collections.newSetFromMap(new IdentityHashMap<>());
			Set<ClassInstance> visitedDown = Collections.newSetFromMap(new IdentityHashMap<>());

			for (Map.Entry<String, String> entry : tasks) {
				String className = getClassName(entry.getKey(), type);
				ClassInstance cls = state.getClass(className);
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

				Propagator.propagate(member, idSrc, nameDst, visitedUp, visitedDown);
			}
		}

		private final MrjState state;
		private final MemberType type;
		private final List<Map.Entry<String, String>> tasks = new ArrayList<>();
	}

	public enum LinkedMethodPropagation {
		/**
		 * Don't propagate names into methods.
		 *
		 * <p>This is JVM compliant but doesn't mirror Javac's behavior and decouples bridge methods from their target.
		 */
		DISABLED,
		/**
		 * Propagate names into methods.
		 *
		 * <p>Mappings reaching bridge method will be applied to the methods they bridge to.
		 */
		ENABLED,
		/**
		 * Propagate names into methods and create additional bridges to keep the normally mapped method name intact.
		 */
		COMPATIBLE
	}

	static final class MrjState implements TrEnvironment {
		MrjState(TinyRemapper tr, int version) {
			Objects.requireNonNull(tr);

			this.tr = tr;
			this.version = version;
			this.remapper = new AsmRemapper(this);
		}

		@Override
		public int getMrjVersion() {
			return version;
		}

		@Override
		public AsmRemapper getRemapper() {
			return remapper;
		}

		@Override
		public ClassInstance getClass(String internalName) {
			return classes.get(internalName);
		}

		@Override
		public void propagate(TrMember m, String newName) {
			MemberInstance member = (MemberInstance) m;
			Set<ClassInstance> visitedUp = Collections.newSetFromMap(new IdentityHashMap<>());
			Set<ClassInstance> visitedDown = Collections.newSetFromMap(new IdentityHashMap<>());

			Propagator.propagate(member, member.getId(), newName, visitedUp, visitedDown);
		}

		final TinyRemapper tr;
		final int version;
		final Map<String, ClassInstance> classes = new HashMap<>();
		final AsmRemapper remapper;
		volatile boolean dirty = true;
	}

	private final boolean check = false;

	private final boolean keepInputData;
	final Set<String> forcePropagation;
	final Set<String> knownIndyBsm;
	final boolean propagatePrivate;
	final LinkedMethodPropagation propagateBridges;
	final LinkedMethodPropagation propagateRecordComponents;
	private final boolean removeFrames;
	private final boolean ignoreConflicts;
	private final boolean resolveMissing;
	private final boolean checkPackageAccess;
	private final boolean fixPackageAccess;
	private final boolean rebuildSourceFilenames;
	private final boolean skipLocalMapping;
	private final boolean renameInvalidLocals;
	private final Pattern invalidLvNamePattern;
	private final boolean inferNameFromSameLvIndex;
	private final List<AnalyzeVisitorProvider> analyzeVisitors;
	private final List<StateProcessor> stateProcessors;
	private final List<ApplyVisitorProvider> preApplyVisitors;
	private final List<ApplyVisitorProvider> postApplyVisitors;
	final Remapper extraRemapper;

	final AtomicReference<Map<InputTag, InputTag[]>> singleInputTags = new AtomicReference<>(Collections.emptyMap()); // cache for tag -> { tag }

	final List<CompletableFuture<?>> pendingReads = new ArrayList<>(); // reads that need to be waited for before continuing processing (assumes lack of external waiting)
	final Map<String, ClassInstance> readClasses = new ConcurrentHashMap<>(); // classes being potentially concurrently read, to be transferred into unsynchronized classes later

	final MrjState defaultState = new MrjState(this, ClassInstance.MRJ_DEFAULT);
	final Map<Integer, MrjState> mrjStates = new HashMap<>();

	{
		mrjStates.put(defaultState.version, defaultState);
	}

	final Map<String, String> classMap = new HashMap<>();
	final Map<String, String> methodMap = new HashMap<>();
	final Map<String, String> methodArgMap = new HashMap<>();
	final Map<String, String> fieldMap = new HashMap<>();
	final Map<MemberInstance, Set<String>> conflicts = new ConcurrentHashMap<>();
	final Set<ClassInstance> classesToMakePublic = Collections.newSetFromMap(new ConcurrentHashMap<>());
	final Set<MemberInstance> membersToMakePublic = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private final Collection<IMappingProvider> mappingProviders;
	final boolean ignoreFieldDesc;
	private final int threadCount;
	private final ExecutorService threadPool;

	private volatile boolean dirty = true; // volatile to make the state debug asserts more reliable, shouldn't actually see concurrent modifications
	private Map<ClassInstance, byte[]> outputBuffer;
}
