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

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.objectweb.asm.Opcodes;

import net.fabricmc.tinyremapper.TinyRemapper.MrjState;
import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrField;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.api.TrMethod;

public final class MemberInstance implements TrField, TrMethod {
	MemberInstance(TrMember.MemberType type, ClassInstance cls, String name, String desc, int access, int index) {
		this.type = type;
		this.cls = cls;
		this.name = name;
		this.desc = desc;
		this.access = access;
		this.index = index;
	}

	@Override
	public MemberType getType() {
		return this.type;
	}

	@Override
	public TrClass getOwner() {
		return this.cls;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public String getDesc() {
		return this.desc;
	}

	@Override
	public int getAccess() {
		return this.access;
	}

	@Override
	public int getIndex() {
		return index;
	}

	public MrjState getContext() {
		return cls.getContext();
	}

	public String getId() {
		return getId(type, name, desc, cls.tr.ignoreFieldDesc);
	}

	public boolean isPublicOrPrivate() {
		return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE)) != 0;
	}

	@Override
	public String getNewName() {
		String ret = newBridgedName;

		return ret != null ? ret : newName;
	}

	public String getNewMappedName() {
		return newName;
	}

	public String getNewBridgedName() {
		return newBridgedName;
	}

	public boolean setNewName(String name, boolean fromBridge) {
		if (name == null) throw new NullPointerException("null name");

		if (fromBridge) {
			boolean ret = newBridgedNameUpdater.compareAndSet(this, null, name);

			return ret || name.equals(newBridgedName);
		} else {
			boolean ret = newNameUpdater.compareAndSet(this, null, name);

			return ret || name.equals(newName);
		}
	}

	public void forceSetNewName(String name) {
		newName = name;
	}

	@Override
	public String toString() {
		return String.format("%s/%s%s", cls.getName(), name, desc);
	}

	public static String getId(TrMember.MemberType type, String name, String desc, boolean ignoreFieldDesc) {
		return type == TrMember.MemberType.METHOD ? getMethodId(name, desc) : getFieldId(name, desc, ignoreFieldDesc);
	}

	public static String getMethodId(String name, String desc) {
		return name.concat(desc);
	}

	public static String getFieldId(String name, String desc, boolean ignoreDesc) {
		return ignoreDesc ? name : name+";;"+desc;
	}

	public static String getNameFromId(TrMember.MemberType type, String id, boolean ignoreFieldDesc) {
		if (ignoreFieldDesc && type == TrMember.MemberType.FIELD) {
			return id;
		} else {
			String separator = type == TrMember.MemberType.METHOD ? "(" : ";;";
			int pos = id.lastIndexOf(separator);
			if (pos < 0) throw new IllegalArgumentException(String.format("invalid %s id: %s", type.name(), id));

			return id.substring(0, pos);
		}
	}

	private static final AtomicReferenceFieldUpdater<MemberInstance, String> newNameUpdater = AtomicReferenceFieldUpdater.newUpdater(MemberInstance.class, String.class, "newName");
	private static final AtomicReferenceFieldUpdater<MemberInstance, String> newBridgedNameUpdater = AtomicReferenceFieldUpdater.newUpdater(MemberInstance.class, String.class, "newBridgedName");

	final TrMember.MemberType type;
	final ClassInstance cls;
	final String name;
	final String desc;
	final int access;
	final int index;
	private volatile String newName;
	private volatile String newBridgedName;
	String newNameOriginatingCls;
	MemberInstance bridgeTarget;
}
