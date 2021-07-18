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

import org.objectweb.asm.Opcodes;

import net.fabricmc.tinyremapper.api.TrMember.MemberType;
import net.fabricmc.tinyremapper.TinyRemapper.Direction;
import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrEnvironment;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.TinyRemapper.LinkedMethodPropagation;
import net.fabricmc.tinyremapper.TinyRemapper.MrjState;

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
	public TrEnvironment getClasspath() {
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
	public TrMember resolveField(String name, String desc) {
		if (desc == null) {
			return this.resolvePartial(MemberType.FIELD, name, ";;");
		} else {
			return this.resolve(MemberType.FIELD, MemberInstance.getFieldId(name, desc, tr.ignoreFieldDesc));
		}
	}

	@Override
	public TrMember resolveMethod(String name, String desc) {
		if (desc == null) {
			return this.resolvePartial(MemberType.METHOD, name, "(");
		} else {
			return this.resolve(MemberType.METHOD, MemberInstance.getMethodId(name, desc));
		}
	}

	@Override
	public Collection<? extends TrMember> getMembers() {
		return this.members.values();
	}

	@Override
	public List<String> getInterfaces() {
		return Collections.unmodifiableList(Arrays.asList(interfaces));
	}

	public boolean isInterface() {
		return (access & Opcodes.ACC_INTERFACE) != 0;
	}

	public boolean isRecord() {
		return (access & Opcodes.ACC_RECORD) != 0;
	}

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
		String idPrefix = MemberInstance.getId(type, name, descPrefix != null ? descPrefix : "", tr.ignoreFieldDesc);
		boolean isField = type == TrMember.MemberType.FIELD;

		MemberInstance member = getMemberPartial(type, idPrefix);
		if (member == nullMember) return null; // non-unique match

		Set<ClassInstance> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		Deque<ClassInstance> queue = new ArrayDeque<>();
		queue.add(this);
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
						MemberInstance ret = parent.getMemberPartial(type, idPrefix);

						if (ret != null) {
							if (ret == nullMember) {
								return null; // non-unique match
							} else if (member == null) {
								member = ret;
							} else if (!member.desc.equals(ret.desc)) {
								return null; // non-unique match
							}
						}

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
							MemberInstance parentMember = parent.getMemberPartial(type, idPrefix);

							if (parentMember != null
									&& (isField || (parentMember.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0)) { // potential match
								if (parentMember == nullMember) {
									return null; // non-unique match
								} else if (member == null) {
									if (!isField && (parentMember.access & (Opcodes.ACC_ABSTRACT)) != 0) {
										if (secondaryMatch != null && !secondaryMatch.desc.equals(parentMember.desc)) {
											return null; // non-unique match
										} else {
											secondaryMatch = parentMember;
										}
									} else {
										member = parentMember;
									}
								} else if (!member.desc.equals(parentMember.desc)) {
									return null; // non-unique match
								}
							}
						}

						queue.addLast(parent);
					}
				}
			} while (!isField && (cls = queue.pollFirst()) != null);
		} while ((context = queue.pollFirst()) != null); // overall-recursion for fields

		if (secondaryMatch == null) {
			return member;
		} else if (member == null) {
			return secondaryMatch;
		} else if (member.desc.equals(secondaryMatch.desc)) {
			return member;
		} else {
			return null; // non-unique match
		}
	}

	private MemberInstance getMemberPartial(MemberType type, String idPrefix) {
		MemberInstance ret = null;

		for (Map.Entry<String, MemberInstance> entry : members.entrySet()) {
			if (entry.getValue().type == type && entry.getKey().startsWith(idPrefix)) {
				if (ret == null) {
					ret = entry.getValue();
				} else {
					return nullMember; // non-unique match
				}
			}
		}

		return ret;
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
	private String name;
	private int mrjVersion;
	private String superName;
	private String signature;
	private int access;
	private String[] interfaces;
}
