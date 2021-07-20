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

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Opcodes;

import net.fabricmc.tinyremapper.TinyRemapper.Direction;
import net.fabricmc.tinyremapper.TinyRemapper.LinkedMethodPropagation;
import net.fabricmc.tinyremapper.TinyRemapper.MrjState;
import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrEnvironment;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.api.TrMember.MemberType;

public final class ClassInstance implements TrClass {
	ClassInstance(TinyRemapper tr, boolean isInput, InputTag[] inputTags, Path srcFile, byte[] data) {
		assert !isInput || data != null;
		this.tr = tr;
		this.isInput = isInput;
		this.inputTags = inputTags;
		this.srcPath = srcFile;
		this.data = data;
		this.mrjOrigin = this;
	}

	void init(int mrjVersion, String name, String sign, String superName, int access, String[] interfaces) {
		this.name = name;
		this.mrjVersion = mrjVersion;
		this.superName = superName;
		this.signature = sign;
		this.access = access;
		this.interfaces = interfaces;
	}

	void setContext(MrjState context) {
		this.context = context;
	}

	MrjState getContext() {
		return context;
	}

	MemberInstance addMember(MemberInstance member) {
		return members.put(member.getId(), member);
	}

	void addInputTags(InputTag[] tags) {
		if (tags == null || tags.length == 0) return;

		InputTag[] oldTags;
		InputTag[] newTags;

		do { // cas loop
			oldTags = inputTags;

			if (oldTags == null) {
				newTags = tags;
			} else { // both old and new tags, merge
				int missingTags = 0;

				for (InputTag newTag : tags) {
					boolean found = false;

					for (InputTag oldTag : oldTags) {
						if (newTag == oldTag) {
							found = true;
							break;
						}
					}

					if (!found) missingTags++;
				}

				if (missingTags == 0) return;

				newTags = Arrays.copyOf(tags, oldTags.length + missingTags);

				for (InputTag newTag : tags) {
					boolean found = false;

					for (InputTag oldTag : oldTags) {
						if (newTag == oldTag) {
							found = true;
							break;
						}
					}

					if (!found) {
						newTags[newTags.length - missingTags] = newTag;
						missingTags--;
					}
				}
			}
		} while (!inputTagsUpdater.compareAndSet(this, oldTags, newTags));
	}

	InputTag[] getInputTags() {
		return inputTags;
	}

	boolean hasAnyInputTag(InputTag[] reqTags) {
		InputTag[] availTags = inputTags;
		if (availTags == null) return true;

		for (InputTag reqTag : reqTags) {
			for (InputTag availTag : availTags) {
				if (availTag == reqTag) {
					return true;
				}
			}
		}

		return false;
	}

	@Override
	public TrEnvironment getEnvironment() {
		return this.context;
	}

	@Override
	public int getAccess() {
		return access;
	}

	@Override
	public String getName() {
		return name;
	}

	public int getMrjVersion() {
		return mrjVersion;
	}

	@Override
	public String getSuperName() {
		return superName;
	}

	@Override
	public String getSignature() {
		return signature;
	}

	@Override
	public List<String> getInterfaces() {
		return Collections.unmodifiableList(Arrays.asList(interfaces));
	}

	private static boolean containsTrClass(Collection<? extends TrClass> collections, TrClass cls) {
		return collections.stream().anyMatch(x -> x.getName().equals(cls.getName()));
	}

	/**
	 * The result is cached in the {@link ClassInstance#resolvedInterfaces}.
	 */
	private List<ClassInstance> resolveInterfaces0() {
		synchronized (this.resolvedInterfaces) {	// this must be sync-ed to avoid race-condition
			if (this.resolvedInterfaces.isEmpty()) {
				// if {@code resolvedInterfaces} is non-empty, it must already been
				// resolved.

				for (ClassInstance parent : this.parents) {
					if (containsTrClass(resolvedInterfaces, parent)) {
						// No need to proceed, {@code parent} must already resolved
					} else {
						if (parent.isInterface()) {
							resolvedInterfaces.add(parent);
						}

						parent.resolveInterfaces0().forEach(cls -> {
							if (cls.isInterface() && !containsTrClass(resolvedInterfaces, cls)) {
								// only add if there is no duplicate
								resolvedInterfaces.add(cls);
							}
						});
					}
				}

				// sort the result based on partial order. This is the best we can do, because it's partial order.
				for (int s = 0; s < resolvedInterfaces.size(); s += 1) {    // 0 to (s - 1) is sorted, inclusive
					for (int i = s; i < resolvedInterfaces.size(); i += 1) {    // find the smallest interface first
						boolean smallest = true;

						for (int j = s; j < resolvedInterfaces.size(); j += 1) {    // iterate to see if i is super-interface of others
							if (containsTrClass(resolvedInterfaces.get(j).resolveInterfaces0(), resolvedInterfaces.get(i))) {
								smallest = false;
								break;
							}
						}

						if (smallest) {
							Collections.swap(resolvedInterfaces, s, i);
							break;
						}
					}
				}
			}
		}

		return this.resolvedInterfaces;
	}

	@Override
	public List<String> resolveInterfaces() {
		return resolveInterfaces0().stream().map(ClassInstance::getName).collect(Collectors.toList());
	}

	/**
	 * Do nothing if a member with same name & desc already exists.
	 */
	private static void putIfAbsentTrMember(Collection<TrMember> collection, TrMember member) {
		boolean noneMatch = collection.stream().noneMatch(
				m -> m.getName().equals(member.getName()) && m.getDesc().equals(member.getDesc())
		);

		if (noneMatch) {
			collection.add(member);
		}
	}

	@Override
	public Collection<TrMember> getMethods(String name, String descPrefix, boolean isDescPrefix, Predicate<TrMember> filter, Collection<TrMember> collection) {
		if (name != null && descPrefix != null && !isDescPrefix) {
			// we can take advantage of map.
			TrMember member = members.get(MemberInstance.getMethodId(name, descPrefix));

			if (collection == null) {
				return Collections.singletonList(member);
			} else {
				putIfAbsentTrMember(collection, member);
				return collection;
			}
		} else {
			Stream<TrMember> result = members.values().stream()
					.filter(m -> {
						boolean isMethod = m.getType().equals(MemberType.METHOD);
						boolean isSameName = (name == null) || m.getName().equals(name);
						boolean isSameDesc = (descPrefix == null) || (isDescPrefix ? m.getDesc().startsWith(descPrefix) : m.getDesc().equals(descPrefix));
						return isMethod && isSameName && isSameDesc && filter.test(m);
					}).map(m -> m);

			if (collection == null) {
				return result.collect(Collectors.toList());
			} else {
				result.forEach(m -> putIfAbsentTrMember(collection, m));
				return collection;
			}
		}
	}

	@Override
	public Collection<TrMember> getFields(String name, String descPrefix, boolean isDescPrefix, Predicate<TrMember> filter, Collection<TrMember> collection) {
		if (name != null && descPrefix != null && !isDescPrefix) {
			// we can take advantage of map.
			TrMember member = members.get(MemberInstance.getFieldId(name, descPrefix, tr.ignoreFieldDesc));

			if (collection == null) {
				return Collections.singletonList(member);
			} else {
				putIfAbsentTrMember(collection, member);
				return collection;
			}
		} else {
			Stream<TrMember> result = members.values().stream()
					.filter(m -> {
						boolean isField = m.getType().equals(MemberType.FIELD);
						boolean isSameName = (name == null) || m.getName().equals(name);
						boolean isSameDesc = (descPrefix == null) || (isDescPrefix ? m.getDesc().startsWith(descPrefix) : m.getDesc().equals(descPrefix));
						return isField && isSameName && isSameDesc && filter.test(m);
					}).map(m -> m);

			if (collection == null) {
				return result.collect(Collectors.toList());
			} else {
				result.forEach(m -> putIfAbsentTrMember(collection, m));
				return collection;
			}
		}
	}

	@Override
	public Collection<TrMember> resolveMethods(String name, String descPrefix, boolean isDescPrefix, Predicate<TrMember> filter, Collection<TrMember> collection) {
		// See: https://docs.oracle.com/javase/specs/jvms/se16/html/jvms-5.html#jvms-5.4.3.3
		// for section 5.4.3.3 & 5.4.3.4
		if (collection == null) {
			collection = new ArrayList<>();
		}

		ClassInstance cls = this;

		// Method resolution attempts to locate the referenced method in C and its superclasses.
		while (cls != null) {
			cls.getMethods(name, descPrefix, isDescPrefix, filter, collection);

			// Java guarantee there is at most one super-class
			cls = cls.parents.stream()
					.filter(parent -> !parent.isInterface())
					.findAny()
					.orElse(null);
		}

		// Method resolution attempts to locate the referenced method in the superinterfaces of the specified class C.
		for (ClassInstance cls2 : resolveInterfaces0()) {
			cls2.getMethods(name, descPrefix, isDescPrefix, filter, collection);
		}

		return collection;
	}

	@Override
	public Collection<TrMember> resolveFields(String name, String descPrefix, boolean isDescPrefix, Predicate<TrMember> filter, Collection<TrMember> collection) {
		// See: https://docs.oracle.com/javase/specs/jvms/se16/html/jvms-5.html#jvms-5.4.3.2
		// for section 5.4.3.2
		if (collection == null) {
			collection = new ArrayList<>();
		}

		final Deque<ClassInstance> deque = new ArrayDeque<>();
		final Set<String> visited = new HashSet<>();

		ClassInstance cls = this;

		while (cls != null) {
			// C declares a field with the name and descriptor specified by the field reference
			cls.getFields(name, descPrefix, isDescPrefix, filter, collection);

			// Field lookup is applied recursively to the direct superinterfaces of the specified class or interface C
			deque.push(cls);

			for (ClassInstance parent : cls.parents) {
				if (parent.isInterface() && visited.add(parent.getName())) {
					parent.getFields(name, descPrefix, isDescPrefix, filter, collection);
					deque.addLast(parent);
				}
			}

			// If C has a superclass S, field lookup is applied recursively to S.
			// Java guarantee there is at most one super-class
			cls = cls.parents.stream()
					.filter(parent -> !parent.isInterface())
					.findAny()
					.orElse(null);
		}

		return collection;
	}

	// TODO: based on JLS, a class cannot be private
	public boolean isPublicOrPrivate() {
		return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE)) != 0;
	}

	public boolean isMrjCopy() {
		return mrjOrigin != this;
	}

	public String[] getInterfaces0() {
		return interfaces;
	}

	public Collection<MemberInstance> getMembers0() {
		return members.values();
	}

	public MemberInstance getMember(MemberType type, String id) {
		return members.get(id);
	}

	public ClassInstance getMrjOrigin() {
		return mrjOrigin;
	}

	/**
	 * Rename the member src to dst and continue propagating in dir.
	 *
	 * @param type Member type.
	 * @param idSrc Existing name.
	 * @param nameDst New name.
	 * @param dir Futher propagation direction.
	 */
	void propagate(TinyRemapper remapper, MemberType type, String originatingCls, String idSrc, String nameDst,
			Direction dir, boolean isVirtual, boolean fromBridge,
			boolean first, Set<ClassInstance> visitedUp, Set<ClassInstance> visitedDown) {
		/*
		 * initial private member or static method in interface: only local
		 * non-virtual: up to matching member (if not already in this), then down until matching again (exclusive)
		 * virtual: all across the hierarchy, only non-private|static can change direction - skip private|static in interfaces
		 */

		MemberInstance member = getMember(type, idSrc);

		if (member != null) {
			if (!first && !isVirtual) { // down propagation from non-virtual (static) member matching the signature again, which starts its own namespace
				return;
			}

			if (first // directly mapped
					|| (member.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0 // not private and not static
					|| remapper.propagatePrivate
					|| !remapper.forcePropagation.isEmpty() && remapper.forcePropagation.contains(name.replace('/', '.')+"."+member.name)) { // don't rename private members unless forced or initial (=dir any)

				if (!member.setNewName(nameDst, fromBridge)) {
					remapper.conflicts.computeIfAbsent(member, x -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(originatingCls+"/"+nameDst);
				} else {
					member.newNameOriginatingCls = originatingCls;
				}
			}

			if (first
					&& ((member.access & Opcodes.ACC_PRIVATE) != 0 // private members don't propagate, but they may get skipped over by overriding virtual methods
					|| type == TrMember.MemberType.METHOD && isInterface() && !isVirtual)) { // non-virtual interface methods don't propagate either, the jvm only resolves direct accesses to them
				return;
			} else if (remapper.propagateBridges != LinkedMethodPropagation.DISABLED
					&& member.cls.isInput
					&& isVirtual
					&& (member.access & Opcodes.ACC_BRIDGE) != 0) {
				assert member.type == TrMember.MemberType.METHOD;

				// try to propagate bridge method mapping to the actual implementation

				MemberInstance bridgeTarget = BridgeHandler.getTarget(member);

				if (bridgeTarget != null) {
					Set<ClassInstance> visitedUpBridge = Collections.newSetFromMap(new IdentityHashMap<>());
					Set<ClassInstance> visitedDownBridge = Collections.newSetFromMap(new IdentityHashMap<>());

					visitedUpBridge.add(member.cls);
					visitedDownBridge.add(member.cls);

					propagate(remapper, TrMember.MemberType.METHOD, originatingCls, bridgeTarget.getId(), nameDst, Direction.DOWN, true, remapper.propagateBridges == LinkedMethodPropagation.COMPATIBLE, false, visitedUpBridge, visitedDownBridge);
				}
			}
		} else { // member == null
			assert !first && (type == TrMember.MemberType.FIELD || !isInterface() || isVirtual);

			// potentially intermediately accessed location, handled through resolution in the remapper
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
			for (ClassInstance node : parents) {
				if (visitedUp.add(node)) {
					node.propagate(remapper, type, originatingCls, idSrc, nameDst,
							Direction.UP, isVirtual, fromBridge,
							false, visitedUp, visitedDown);
				}
			}
		}

		if (dir == Direction.ANY || dir == Direction.DOWN || isVirtual && member != null && (member.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0) {
			for (ClassInstance node : children) {
				if (visitedDown.add(node)) {
					node.propagate(remapper, type, originatingCls, idSrc, nameDst,
							Direction.DOWN, isVirtual, fromBridge,
							false, visitedUp, visitedDown);
				}
			}
		}
	}

	/**
	 * Determine whether one type is assignable to another.
	 *
	 * <p>Primitive types including void need to be identical to match.
	 */
	static boolean isAssignableFrom(String superDesc, int superDescStart, String subDesc, int subDescStart, MrjState context) {
		char superType = superDesc.charAt(superDescStart);
		char subType = subDesc.charAt(subDescStart);

		// allow only same or object <- array
		if (superType == '[') {
			// require same array

			do {
				if (subType != '[') return false;

				superType = superDesc.charAt(++superDescStart);
				subType = subDesc.charAt(++subDescStart);
			} while (superType == '[');

			return superType == subType
					&& (superType != 'L' || superDesc.regionMatches(superDescStart + 1, subDesc, subDescStart + 1, superDesc.indexOf(';', superDescStart + 1) + 1));
		} else if (superType != 'L') {
			return superType == subType;
		} else if (subType != 'L' && subType != '[') {
			return false;
		}

		// skip L
		superDescStart++;
		subDescStart++;

		// everything is assignable to Object
		if (superDesc.startsWith(objectClassName+";", superDescStart)) return true;

		// non-object sub type can't match anymore
		if (subType != 'L') return false;

		int superDescEnd = superDesc.indexOf(';', superDescStart);
		int subDescEnd = subDesc.indexOf(';', subDescStart);
		int superDescLen = superDescEnd - superDescStart;

		// check super == sub
		if (superDescLen == subDescEnd - subDescStart
				&& superDesc.regionMatches(superDescStart, subDesc, subDescStart, superDescLen)) {
			return true;
		}

		// check super <- sub

		String superName = superDesc.substring(superDescStart, superDescEnd);
		String subName = subDesc.substring(subDescStart, subDescEnd);

		ClassInstance superCls = context.getClass0(superName);
		if (superCls != null && superCls.children.isEmpty()) return false;

		ClassInstance subCls = context.getClass0(subName);

		if (subCls != null) { // sub class known, search upwards
			if (superCls == null || superCls.isInterface()) {
				Set<ClassInstance> visited = Collections.newSetFromMap(new IdentityHashMap<>());
				Deque<ClassInstance> queue = new ArrayDeque<>();
				visited.add(subCls);

				do {
					for (ClassInstance parent : subCls.parents) {
						if (parent.name.equals(superName)) return true;

						if (visited.add(parent)) {
							queue.addLast(parent);
						}
					}
				} while ((subCls = queue.pollFirst()) != null);
			} else {
				do {
					String curSuperName = subCls.superName;

					if (curSuperName.equals(superName)) return true;
					if (curSuperName.equals(objectClassName)) return false;

					subCls = context.getClass0(curSuperName);
				} while (subCls != null);
			}
		} else if (superCls != null) { // only super class known, search down
			Set<ClassInstance> visited = Collections.newSetFromMap(new IdentityHashMap<>());
			Deque<ClassInstance> queue = new ArrayDeque<>();
			visited.add(superCls);

			do {
				for (ClassInstance child : superCls.children) {
					if (child.name.equals(subName)) return true;

					if (visited.add(child)) {
						queue.addLast(child);
					}
				}
			} while ((superCls = queue.pollFirst()) != null);
		}

		// no match or not enough information (incomplete class path)

		return false;
	}

	public MemberInstance resolve(MemberType type, String id) {
		MemberInstance member = getMember(type, id);
		if (member != null) return member;

		// get from cache
		member = resolvedMembers.get(id);

		if (member == null) {
			// compute
			member = resolve0(type, id);
			assert member != null;

			// put in cache
			MemberInstance prev = resolvedMembers.putIfAbsent(id, member);
			if (prev != null) member = prev;
		}

		return member != nullMember ? member : null;
	}

	// This is more efficient than getMethods or getFields, and this method cache result
	private MemberInstance resolve0(MemberType type, String id) {
		boolean isField = type == TrMember.MemberType.FIELD;
		Set<ClassInstance> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		Deque<ClassInstance> queue = new ArrayDeque<>();
		visited.add(this);
		ClassInstance context = this;
		MemberInstance secondaryMatch = null;

		do { // overall-recursion for fields
			// step 1
			// method: search in all super classes recursively
			// field: search in all direct super interfaces recursively

			ClassInstance cls = context;

			do {
				for (ClassInstance parent : cls.parents) {
					if (parent.isInterface() == isField && visited.add(parent)) {
						MemberInstance ret = parent.getMember(type, id);
						if (ret != null) return ret;

						queue.addLast(parent);
					}
				}
			} while ((cls = queue.pollLast()) != null);

			if (!isField) {
				visited.clear();
				visited.add(context);
			}

			// step 2
			// method: search for non-static, non-private, non-abstract in all super interfaces recursively
			//         (breadth first search to obtain the potentially maximally-specific superinterface directly)
			// field: search in all super classes recursively (self-lookup and queue only, outer loop will recurse)
			// step 3
			// method: search for non-static, non-private in all super interfaces recursively

			// step 3 is a super set of step 2 with any option being able to be "arbitrarily chosen" as per the jvm
			// spec, so step 2 ignoring the "exactly one" match requirement doesn't matter and >potentially<
			// maximally-specific superinterface is good enough

			cls = context;

			do {
				for (ClassInstance parent : cls.parents) {
					if ((!isField || !parent.isInterface()) && visited.add(parent)) { // field -> class, method -> any
						if (parent.isInterface() != isField) { // field -> class, method -> interface; look in parent
							MemberInstance parentMember = parent.getMember(type, id);

							if (parentMember != null
									&& (isField || (parentMember.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0)) { // potential match
								if (!isField && (parentMember.access & (Opcodes.ACC_ABSTRACT)) != 0) {
									secondaryMatch = parentMember;
								} else {
									return parentMember;
								}
							}
						}

						queue.addLast(parent);
					}
				}
			} while (!isField && (cls = queue.pollFirst()) != null);
		} while ((context = queue.pollFirst()) != null); // overall-recursion for fields

		return secondaryMatch != null ? secondaryMatch : nullMember;
	}

	public MemberInstance resolvePartial(MemberType type, String name, String descPrefix) {
		if (type.equals(MemberType.FIELD)) {
			Collection<TrMember> member = resolveFields(name, descPrefix, true, null);

			if (member.size() == 1) {
				return (MemberInstance) member.stream().findAny().get();
			} else {
				return null;
			}
		} else if (type.equals(MemberType.METHOD)) {
			Collection<TrMember> member = resolveMethods(name, descPrefix, true, null);

			if (member.size() == 1) {
				return (MemberInstance) member.stream().findAny().get();
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	ClassInstance constructMrjCopy(MrjState newContext) {
		// isInput should be false, since the MRJ copy should not be emitted
		ClassInstance copy = new ClassInstance(tr, false, inputTags, srcPath, data);
		copy.init(mrjVersion, name, signature, superName, access, interfaces);
		copy.setContext(newContext);

		for (MemberInstance member : members.values()) {
			copy.addMember(new MemberInstance(member.type, copy, member.name, member.desc, member.access));
		}

		// set the origin
		copy.mrjOrigin = mrjOrigin;
		return copy;
	}

	@Override
	public String toString() {
		return name;
	}

	public static String getMrjName(String clsName, int mrjVersion) {
		if (mrjVersion != MRJ_DEFAULT) {
			return MRJ_PREFIX + "/" + mrjVersion + "/" + clsName;
		} else {
			return clsName;
		}
	}

	public static final int MRJ_DEFAULT = -1;
	public static final String MRJ_PREFIX = "/META-INF/versions";

	private static final String objectClassName = "java/lang/Object";
	private static final MemberInstance nullMember = new MemberInstance(null, null, null, null, 0);
	private static final AtomicReferenceFieldUpdater<ClassInstance, InputTag[]> inputTagsUpdater = AtomicReferenceFieldUpdater.newUpdater(ClassInstance.class, InputTag[].class, "inputTags");

	final TinyRemapper tr;
	private MrjState context;

	final boolean isInput;
	private volatile InputTag[] inputTags; // cow input tag list, null for none
	final Path srcPath;
	byte[] data;
	private ClassInstance mrjOrigin;
	private final Map<String, MemberInstance> members = new HashMap<>(); // methods and fields are distinct due to their different desc separators
	private final ConcurrentMap<String, MemberInstance> resolvedMembers = new ConcurrentHashMap<>();
	final Set<ClassInstance> parents = new HashSet<>();
	final Set<ClassInstance> children = new HashSet<>();
	// This guarantees a partial order such that for two interface A, B; A will appear before B if A extends B
	private final List<ClassInstance> resolvedInterfaces = new ArrayList<>();

	private String name;
	private int mrjVersion;
	private String superName;
	private String signature;
	private int access;
	private String[] interfaces;
}
