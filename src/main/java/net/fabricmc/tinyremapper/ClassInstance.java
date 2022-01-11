/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2019, 2021, FabricMC
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

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import net.fabricmc.tinyremapper.TinyRemapper.Direction;
import net.fabricmc.tinyremapper.TinyRemapper.LinkedMethodPropagation;
import net.fabricmc.tinyremapper.TinyRemapper.MrjState;
import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrEnvironment;
import net.fabricmc.tinyremapper.api.TrField;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.api.TrMember.MemberType;
import net.fabricmc.tinyremapper.api.TrMethod;

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

	void init(String name, int classVersion, int mrjVersion, String signature, String superName, int access, String[] interfaces) {
		this.name = name;
		this.classVersion = classVersion;
		this.mrjVersion = mrjVersion;
		this.superName = superName;
		this.signature = signature;
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

	public int getClassVersion() {
		return classVersion;
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
	public ClassInstance getSuperClass() {
		for (ClassInstance cls : parents) {
			if (!cls.isInterface()) return cls;
		}

		return null;
	}

	@Override
	public List<ClassInstance> getInterfaces() {
		List<ClassInstance> ret = new ArrayList<>(parents.size());

		for (ClassInstance cls : parents) {
			if (cls.isInterface()) ret.add(cls);
		}

		return ret;
	}

	@Override
	public List<String> getInterfaceNames() {
		return Collections.unmodifiableList(Arrays.asList(interfaces));
	}

	String[] getInterfaceNames0() {
		return interfaces;
	}

	@Override
	public Collection<ClassInstance> getParents() {
		return parents;
	}

	@Override
	public Collection<ClassInstance> getChildren() {
		return children;
	}

	public boolean isPublicOrPrivate() {
		return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE)) != 0;
	}

	public boolean isMrjCopy() {
		return mrjOrigin != this;
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
	void propagate(MemberType type, String originatingCls, String idSrc, String nameDst,
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
					|| tr.propagatePrivate
					|| !tr.forcePropagation.isEmpty() && tr.forcePropagation.contains(name.replace('/', '.')+"."+member.name)) { // don't rename private members unless forced or initial (=dir any)

				if (!member.setNewName(nameDst, fromBridge)) {
					tr.conflicts.computeIfAbsent(member, x -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(originatingCls+"/"+nameDst);
				} else {
					member.newNameOriginatingCls = originatingCls;
				}
			}

			if (first
					&& ((member.access & Opcodes.ACC_PRIVATE) != 0 // private members don't propagate, but they may get skipped over by overriding virtual methods
					|| type == TrMember.MemberType.METHOD && isInterface() && !isVirtual)) { // non-virtual interface methods don't propagate either, the jvm only resolves direct accesses to them
				return;
			} else if (tr.propagateBridges != LinkedMethodPropagation.DISABLED
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

					propagate(TrMember.MemberType.METHOD, originatingCls, bridgeTarget.getId(), nameDst, Direction.DOWN, true, tr.propagateBridges == LinkedMethodPropagation.COMPATIBLE, false, visitedUpBridge, visitedDownBridge);
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
					node.propagate(type, originatingCls, idSrc, nameDst,
							Direction.UP, isVirtual, fromBridge,
							false, visitedUp, visitedDown);
				}
			}
		}

		if (dir == Direction.ANY || dir == Direction.DOWN || isVirtual && member != null && (member.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0) {
			for (ClassInstance node : children) {
				if (visitedDown.add(node)) {
					node.propagate(type, originatingCls, idSrc, nameDst,
							Direction.DOWN, isVirtual, fromBridge,
							false, visitedUp, visitedDown);
				}
			}
		}
	}

	@Override
	public boolean isAssignableFrom(TrClass cls) {
		return cls instanceof ClassInstance && isAssignableFrom((ClassInstance) cls);
	}

	public boolean isAssignableFrom(ClassInstance cls) {
		if (cls == this) return true;

		if (isInterface()) {
			Set<ClassInstance> visited = Collections.newSetFromMap(new IdentityHashMap<>());
			Deque<ClassInstance> queue = new ArrayDeque<>();
			visited.add(cls);

			do {
				for (ClassInstance parent : cls.parents) {
					if (parent == this) return true;

					if (visited.add(parent)) {
						queue.addLast(parent);
					}
				}
			} while ((cls = queue.pollFirst()) != null);
		} else {
			do {
				ClassInstance superCls = null;

				for (ClassInstance c : cls.parents) {
					if (!c.isInterface()) {
						if (c == this) return true;
						superCls = c;
						break;
					}
				}

				cls = superCls;
			} while (cls != null);
		}

		return false;
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

		ClassInstance superCls = context.getClass(superName);
		if (superCls != null && superCls.children.isEmpty()) return false;

		ClassInstance subCls = context.getClass(subName);

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

					subCls = context.getClass(curSuperName);
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

	@Override
	public MemberInstance getField(String name, String desc) {
		return members.get(MemberInstance.getFieldId(name, desc, tr.ignoreFieldDesc));
	}

	@Override
	public MemberInstance getMethod(String name, String desc) {
		return members.get(MemberInstance.getMethodId(name, desc));
	}

	public MemberInstance getMember(MemberType type, String id) {
		return members.get(id);
	}

	@Override
	public Collection<? extends TrField> getFields() {
		List<TrField> ret = new ArrayList<>(members.size());

		for (MemberInstance m : members.values()) {
			if (m.isField()) ret.add(m);
		}

		return ret;
	}

	@Override
	public Collection<? extends TrMethod> getMethods() {
		List<TrMethod> ret = new ArrayList<>(members.size());

		for (MemberInstance m : members.values()) {
			if (m.isMethod()) ret.add(m);
		}

		return ret;
	}

	@Override
	public Collection<MemberInstance> getMembers() {
		return members.values();
	}

	@Override
	public Collection<TrField> getFields(String name, String desc, boolean isDescPrefix, Predicate<TrField> filter, Collection<TrField> out) {
		if (out == null) out = new ArrayList<>(members.size());

		for (MemberInstance m : members.values()) {
			if (m.isField() && matches(m, name, desc, isDescPrefix, filter)) out.add(m);
		}

		return out;
	}

	@Override
	public Collection<TrMethod> getMethods(String name, String desc, boolean isDescPrefix, Predicate<TrMethod> filter, Collection<TrMethod> out) {
		if (out == null) out = new ArrayList<>(members.size());

		for (MemberInstance m : members.values()) {
			if (m.isMethod() && matches(m, name, desc, isDescPrefix, filter)) out.add(m);
		}

		return out;
	}

	@Override
	public MemberInstance resolveField(String name, String desc) {
		return resolve(MemberType.FIELD, MemberInstance.getFieldId(name, desc, tr.ignoreFieldDesc));
	}

	@Override
	public MemberInstance resolveMethod(String name, String desc) {
		return resolve(MemberType.METHOD, MemberInstance.getMethodId(name, desc));
	}

	public MemberInstance resolve(MemberType type, String id) {
		MemberInstance member = getMember(type, id);
		if (member != null) return member;

		// get from cache
		member = resolvedMembers.get(id);

		if (member == null) {
			// compute
			member = type == MemberType.FIELD ? resolveField(id) : resolveMethod(id);
			assert member != null;

			// put in cache
			MemberInstance prev = resolvedMembers.putIfAbsent(id, member);
			if (prev != null) member = prev;
		}

		return member != nullMember ? member : null;
	}

	private MemberInstance resolveField(String id) {
		Deque<ClassInstance> queue = new ArrayDeque<>();
		Set<ClassInstance> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		visited.add(this);
		ClassInstance context = this;

		for (;;) { // overall-recursion for fields
			// step 1
			// search in all direct super interfaces recursively

			ClassInstance cls = context;

			do {
				for (ClassInstance parent : cls.parents) {
					if (parent.isInterface() && visited.add(parent)) {
						MemberInstance ret = parent.getMember(MemberType.FIELD, id);
						if (ret != null) return ret;

						queue.addLast(parent);
					}
				}
			} while ((cls = queue.pollLast()) != null);

			// step 2
			// search in all super classes recursively (self-lookup and queue only, outer loop will recurse)

			cls = context;
			context = cls.getSuperClass();
			if (context == null) break;

			MemberInstance parentMember = context.getMember(MemberType.FIELD, id);
			if (parentMember != null) return parentMember;
		}

		return nullMember;
	}

	@Override
	public Collection<TrField> resolveFields(String name, String desc, boolean isDescPrefix, Predicate<TrField> filter, Collection<TrField> out) {
		if (name != null && (desc != null && !isDescPrefix || tr.ignoreFieldDesc)) {
			MemberInstance ret = resolve(MemberType.FIELD, MemberInstance.getFieldId(name, desc, tr.ignoreFieldDesc));
			if (ret != null && filter != null && !filter.test(ret)) ret = null;

			if (out == null) {
				return ret == null || filter != null ? Collections.emptyList() : Collections.singletonList(ret);
			} else {
				if (ret != null) out.add(ret);
				return out;
			}
		}

		if (out == null) out = new ArrayList<>();

		for (MemberInstance member : getMembers()) {
			if (member.isField()) ClassInstance.<TrField>addMatching(member, name, desc, isDescPrefix, filter, out);
		}

		Deque<ClassInstance> queue = new ArrayDeque<>();
		Set<ClassInstance> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		visited.add(this);
		ClassInstance context = this;

		for (;;) { // overall-recursion for fields
			// step 1
			// search in all direct super interfaces recursively

			ClassInstance cls = context;

			do {
				for (ClassInstance parent : cls.parents) {
					if (parent.isInterface() && visited.add(parent)) {
						for (MemberInstance member : parent.getMembers()) {
							if (member.isField()) addMatching(member, name, desc, isDescPrefix, filter, out);
						}

						queue.addLast(parent);
					}
				}
			} while ((cls = queue.pollLast()) != null);

			// step 2
			// search in all super classes recursively (self-lookup and queue only, outer loop will recurse)

			cls = context;
			context = cls.getSuperClass();
			if (context == null) break;

			for (MemberInstance member : context.getMembers()) {
				if (member.isField()) addMatching(member, name, desc, isDescPrefix, filter, out);
			}
		}

		return out;
	}

	private MemberInstance resolveMethod(String id) {
		// step 1
		// search in all super classes recursively

		ClassInstance cls = this;

		while ((cls = cls.getSuperClass()) != null) {
			MemberInstance ret = cls.getMember(MemberType.METHOD, id);
			if (ret != null) return ret;
		}

		// step 2
		// search for non-static, non-private, non-abstract in all super interfaces recursively
		// (breadth first search to obtain the potentially maximally-specific superinterface directly)
		// step 3
		// method: search for non-static, non-private in all super interfaces recursively

		// step 3 is a super set of step 2 with any option being able to be "arbitrarily chosen" as per the jvm
		// spec, so step 2 ignoring the "exactly one" match requirement doesn't matter and >potentially<
		// maximally-specific superinterface is good enough

		Deque<ClassInstance> queue = new ArrayDeque<>();
		Set<ClassInstance> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		visited.add(this);
		List<MemberInstance> matchedMethods = new ArrayList<>();
		boolean hasNonAbstract = false;
		cls = this;

		do {
			for (ClassInstance parent : cls.parents) {
				if (!visited.add(parent)) continue;

				if (parent.isInterface()) {
					MemberInstance parentMember = parent.getMember(MemberType.METHOD, id);

					if (parentMember != null && parentMember.isVirtual()) { // potential match
						if (!parentMember.isAbstract()) hasNonAbstract = true;
						matchedMethods.add(parentMember);
						continue; // skip queuing, sub classes aren't relevant for maximally-specific selection
					}
				}

				queue.addLast(parent);
			}
		} while ((cls = queue.pollFirst()) != null);

		if (hasNonAbstract && matchedMethods.size() > 1) {
			// try to select first maximally-specific superinterface method (doesn't matter if it's the only one, jvm spec allows arbitrary choice otherwise)
			memberLoop: for (MemberInstance member : matchedMethods) {
				if (member.isAbstract()) continue;

				for (MemberInstance m : matchedMethods) {
					if (m != member && member.cls.isAssignableFrom(m.cls)) continue memberLoop;
				}

				return member;
			}
		}

		if (!matchedMethods.isEmpty()) return matchedMethods.get(0);

		return nullMember;
	}

	@Override
	public Collection<TrMethod> resolveMethods(String name, String desc, boolean isDescPrefix, Predicate<TrMethod> filter, Collection<TrMethod> out) {
		if (name != null && desc != null && !isDescPrefix) {
			MemberInstance ret = resolve(MemberType.METHOD, MemberInstance.getMethodId(name, desc));
			if (ret != null && filter != null && !filter.test(ret)) ret = null;

			if (out == null) {
				return ret == null || filter != null ? Collections.emptyList() : Collections.singletonList(ret);
			} else {
				if (ret != null) out.add(ret);
				return out;
			}
		}

		if (out == null) out = new ArrayList<>();

		for (MemberInstance member : getMembers()) {
			if (member.isMethod()) addMatching(member, name, desc, isDescPrefix, filter, out);
		}

		// step 1
		// search in all super classes recursively

		ClassInstance cls = this;

		while ((cls = cls.getSuperClass()) != null) {
			for (MemberInstance member : cls.getMembers()) {
				if (member.isMethod()) addMatching(member, name, desc, isDescPrefix, filter, out);
			}
		}

		// step 2
		// search for non-static, non-private, non-abstract in all super interfaces recursively
		// (breadth first search to obtain the potentially maximally-specific superinterface directly)
		// step 3
		// method: search for non-static, non-private in all super interfaces recursively

		// step 3 is a super set of step 2 with any option being able to be "arbitrarily chosen" as per the jvm
		// spec, so step 2 ignoring the "exactly one" match requirement doesn't matter and >potentially<
		// maximally-specific superinterface is good enough

		Deque<ClassInstance> queue = new ArrayDeque<>();
		Set<ClassInstance> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		visited.add(this);
		Map<String, List<TrMethod>> matchedMethodsMap = new HashMap<>();
		boolean hasNonAbstract = false;
		cls = this;

		do {
			for (ClassInstance parent : cls.parents) {
				if (!visited.add(parent)) continue;

				if (parent.isInterface()) {
					for (MemberInstance member : parent.getMembers()) {
						if (member.isMethod() && matches(member, name, desc, isDescPrefix, filter)) {
							if (addUnique(member, matchedMethodsMap.computeIfAbsent(member.getId(), ignore -> new ArrayList<>()))) {
								if (!member.isAbstract()) hasNonAbstract = true;
							}
						}
					}
				}

				queue.addLast(parent);
			}
		} while ((cls = queue.pollFirst()) != null);

		mapLoop: for (List<TrMethod> matchedMethods : matchedMethodsMap.values()) {
			if (matchedMethods.isEmpty()) continue;

			if (hasNonAbstract && matchedMethods.size() > 1) {
				memberLoop: for (TrMethod member : matchedMethods) {
					if (member.isAbstract()) continue;

					for (TrMember m : matchedMethods) {
						if (m != member && member.getOwner().isAssignableFrom(m.getOwner())) continue memberLoop;
					}

					addUnique(member, out);

					continue mapLoop;
				}
			}

			addUnique(matchedMethods.get(0), out);
		}

		return out;
	}

	private static <T extends TrMember> boolean matches(T member, String name, String desc, boolean isDescPrefix, Predicate<T> filter) {
		return (name == null || name.equals(member.getName()))
				&& (desc == null || !isDescPrefix && member.getDesc().equals(desc) || isDescPrefix && member.getDesc().startsWith(desc))
				&& (filter == null || filter.test(member));
	}

	private static <T extends TrMember> boolean addUnique(T member, Collection<T> out) {
		for (T m : out) {
			if (m.getName().equals(member.getName()) && m.getDesc().equals(member.getDesc())) return false;
		}

		out.add(member);

		return true;
	}

	private static <T extends TrMember> void addMatching(T member, String name, String desc, boolean isDescPrefix, Predicate<T> filter, Collection<T> out) {
		if (matches(member, name, desc, isDescPrefix, filter)) {
			addUnique(member, out);
		}
	}

	ClassInstance constructMrjCopy(MrjState newContext) {
		// isInput should be false, since the MRJ copy should not be emitted
		ClassInstance copy = new ClassInstance(tr, false, inputTags, srcPath, data);
		copy.init(name, classVersion, mrjVersion, signature, superName, access, interfaces);
		copy.setContext(newContext);

		for (MemberInstance member : members.values()) {
			copy.addMember(new MemberInstance(member.type, copy, member.name, member.desc, member.access, member.index));
		}

		// set the origin
		copy.mrjOrigin = mrjOrigin;
		return copy;
	}

	@Override
	public void accept(ClassVisitor cv, int readerFlags) {
		if (data == null) throw new IllegalStateException("data unavailable");

		new ClassReader(data).accept(cv, readerFlags);
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
	private static final MemberInstance nullMember = new MemberInstance(null, null, null, null, 0, 0);
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
	private String name;
	private int classVersion;
	private int mrjVersion;
	private String superName;
	private String signature;
	private int access;
	private String[] interfaces;
}
