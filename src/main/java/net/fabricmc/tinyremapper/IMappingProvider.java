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

@FunctionalInterface
public interface IMappingProvider {
	void load(MappingAcceptor out);

	public interface MappingAcceptor {
		void acceptClass(String srcName, String dstName);
		void acceptMethod(Member method, String dstName);
		void acceptMethodArg(Member method, int lvIndex, String dstName);
		void acceptMethodVar(Member method, int lvIndex, int startOpIdx, int asmIndex, String dstName);
		void acceptField(Member field, String dstName);
	}

	final class Member {
		public Member(String owner, String name, String desc) {
			this.owner = owner;
			this.name = name;
			this.desc = desc;
		}

		public String owner;
		public String name;
		public String desc;
	}
}
