/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2021, FabricMC
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

package net.fabricmc.tinyremapper.extension.mixin.soft.data;

import java.util.Objects;

import net.fabricmc.tinyremapper.api.TrMember.MemberType;
import net.fabricmc.tinyremapper.extension.mixin.common.StringUtility;

public final class MemberInfo {
	private final String owner;		// desc
	private final String name;		// name
	private final String quantifier;
	private final String desc;		// desc

	public MemberInfo(String owner, String name, String quantifier, String desc) {
		this.owner = Objects.requireNonNull(owner);
		this.name = Objects.requireNonNull(name);
		this.quantifier = Objects.requireNonNull(quantifier);
		this.desc = Objects.requireNonNull(desc);
	}

	public String getOwner() {
		return owner;
	}

	public String getName() {
		return name;
	}

	public String getQuantifier() {
		return quantifier;
	}

	public String getDesc() {
		return desc;
	}

	public MemberType getType() {
		if (desc.isEmpty()) {
			return null;
		}

		return StringUtility.isMethodDesc(desc) ? MemberType.METHOD : MemberType.FIELD;
	}

	public boolean isFullyQualified() {
		return !(owner.isEmpty() || name.isEmpty() || desc.isEmpty());
	}

	public static boolean isRegex(String str) {
		return str.endsWith("/");
	}

	public static boolean isDynamic(String str) {
		return str.startsWith("@");
	}

	public static MemberInfo parse(String str) {
		if (isRegex(str) || isDynamic(str)) {
			return null;
		}

		str = str.replaceAll("\\s", "");

		// str = owner | name | quantifier | descriptor

		int sep;
		String owner, name, quantifier, descriptor;
		owner = name = quantifier = descriptor = "";

		if ((sep = str.indexOf('(')) >= 0) {
			descriptor = str.substring(sep);
			str = str.substring(0, sep);
		} else if ((sep = str.indexOf(":")) >= 0) {
			descriptor = str.substring(sep + 1);
			str = str.substring(0, sep);
		}

		// str = owner | name | quantifier

		if ((sep = str.indexOf('*')) >= 0) {
			quantifier = str.substring(sep);
			str = str.substring(0, sep);
		} else if ((sep = str.indexOf('+')) >= 0) {
			quantifier = str.substring(sep);
			str = str.substring(0, sep);
		} else if ((sep = str.indexOf('{')) >= 0) {
			quantifier = str.substring(sep);
			str = str.substring(0, sep);
		}

		// str = owner | name

		if ((sep = str.indexOf(';')) >= 0) {
			owner = StringUtility.classDescToName(str.substring(0, sep + 1));
			str = str.substring(sep + 1);
		} else if ((sep = str.lastIndexOf('.')) >= 0) {
			owner = str.substring(0, sep).replace('.', '/');
			str = str.substring(sep + 1);
		}

		// str = owner or name
		if (str.contains("/") || str.contains(".")) {
			owner = str.replace('.', '/');
		} else {
			name = str;
		}

		return new MemberInfo(owner, name, quantifier, descriptor);
	}

	@Override
	public String toString() {
		String owner = getOwner().isEmpty() ? "" : StringUtility.classNameToDesc(getOwner());
		String desc = getDesc().isEmpty() ? "" : (Objects.equals(getType(), MemberType.FIELD) ? ":" : "") + getDesc();

		return owner + name + quantifier + desc;
	}
}
