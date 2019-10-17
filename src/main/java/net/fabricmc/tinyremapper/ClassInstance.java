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
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.objectweb.asm.Opcodes;

import net.fabricmc.tinyremapper.MemberInstance.MemberType;
import net.fabricmc.tinyremapper.TinyRemapper.Direction;

public final class ClassInstance {
	ClassInstance(TinyRemapper context, boolean isInput, Path srcFile, byte[] data) {
		this.context = context;
		this.isInput = isInput;
		this.srcPath = srcFile;
		this.data = data;
	}

	void init(String name, String superName, int access, String[] interfaces) {
		this.name = name;
		this.superName = superName;
		this.access = access;
		this.interfaces = interfaces;
	}

	MemberInstance addMember(MemberInstance member) {
		return members.put(member.getId(), member);
	}

	public String getName() {
		return name;
	}

	public String getSuperName() {
		return superName;
	}

	public boolean isInterface() {
		return (access & Opcodes.ACC_INTERFACE) != 0;
	}

	public boolean isPublicOrPrivate() {
		return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE)) != 0;
	}

	public String[] getInterfaces() {
		return interfaces;
	}

	public Collection<MemberInstance> getMembers() {
		return members.values();
	}

	public MemberInstance getMember(MemberType type, String id) {
		return members.get(id);
	}

	/**
	 * Rename the member src to dst and continue propagating in dir.
	 *
	 * @param type Member type.
	 * @param idSrc Existing name.
	 * @param idDst New name.
	 * @param dir Futher propagation direction.
	 */
	void propagate(TinyRemapper remapper, MemberType type, String originatingCls, String idSrc, String nameDst, Direction dir, boolean isVirtual, boolean first, Set<ClassInstance> visitedUp, Set<ClassInstance> visitedDown) {
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

				if (!member.setNewName(nameDst)) {
					remapper.conflicts.computeIfAbsent(member, x -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(originatingCls+"/"+nameDst);
				} else {
					member.newNameOriginatingCls = originatingCls;
				}
			}

			if (first
					&& ((member.access & Opcodes.ACC_PRIVATE) != 0 // private members don't propagate, but they may get skipped over by overriding virtual methods
					|| type == MemberType.METHOD && isInterface() && !isVirtual)) { // non-virtual interface methods don't propagate either, the jvm only resolves direct accesses to them
				return;
			}
		} else { // member == null
			assert !first && (type == MemberType.FIELD || !isInterface() || isVirtual);

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
					node.propagate(remapper, type, originatingCls, idSrc, nameDst, Direction.UP, isVirtual, false, visitedUp, visitedDown);
				}
			}
		}

		if (dir == Direction.ANY || dir == Direction.DOWN || isVirtual && member != null && (member.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0) {
			for (ClassInstance node : children) {
				if (visitedDown.add(node)) {
					node.propagate(remapper, type, originatingCls, idSrc, nameDst, Direction.DOWN, isVirtual, false, visitedUp, visitedDown);
				}
			}
		}
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
		boolean isField = type == MemberType.FIELD;
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
		String idPrefix = MemberInstance.getId(type, name, descPrefix != null ? descPrefix : "", context.ignoreFieldDesc);
		boolean isField = type == MemberType.FIELD;

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

	@Override
	public String toString() {
		return name;
	}

	private static final MemberInstance nullMember = new MemberInstance(null, null, null, null, 0);

	final TinyRemapper context;
	final boolean isInput;
	final Path srcPath;
	final byte[] data;
	private final Map<String, MemberInstance> members = new HashMap<>(); // methods and fields are distinct due to their different desc separators
	private final ConcurrentMap<String, MemberInstance> resolvedMembers = new ConcurrentHashMap<>();
	final Set<ClassInstance> parents = new HashSet<>();
	final Set<ClassInstance> children = new HashSet<>();
	private String name;
	private String superName;
	private int access;
	private String[] interfaces;
}