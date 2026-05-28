/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2026, FabricMC
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

package net.fabricmc.tinyremapper.extension.mixin.soft.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RegexMatcher {
	private static final Pattern PATTERN = Pattern.compile("((owner|name|desc)\\s*=\\s*)?/(.*?)(?<!\\\\)/");

	private final Pattern owner;
	private final Pattern name;
	private final Pattern desc;

	private RegexMatcher(Pattern owner, Pattern name, Pattern desc) {
		this.owner = owner;
		this.name = name;
		this.desc = desc;
	}

	public static RegexMatcher parse(String input) throws ParsingException {
		Matcher matcher = PATTERN.matcher(input);

		Pattern owner = null;
		Pattern name = null;
		Pattern desc = null;

		while (matcher.find()) {
			Pattern pattern;

			try {
				pattern = Pattern.compile(matcher.group(3));
			} catch (PatternSyntaxException e) {
				throw new ParsingException(String.format("Error parsing pattern %s", matcher.group(3)), e);
			}

			String key = matcher.group(2);

			if (key != null && key.equals("owner")) {
				if (owner != null) {
					throw new ParsingException(String.format("Duplicate owner field, old=/%s/, new=/%s/", owner.pattern(), pattern.pattern()));
				} else {
					owner = pattern;
				}
			} else if (key != null && key.equals("desc")) {
				if (desc != null) {
					throw new ParsingException(String.format("Duplicate desc field, old=/%s/, new=/%s/", desc.pattern(), pattern.pattern()));
				} else {
					desc = pattern;
				}
			} else {
				if (name != null) {
					throw new ParsingException(String.format("Duplicate name field, old=/%s/, new=/%s/", name.pattern(), pattern.pattern()));
				} else {
					name = pattern;
				}
			}
		}

		return new RegexMatcher(owner, name, desc);
	}

	public boolean matches(String owner, String name, String desc) {
		return matches0(owner, this.owner) && matches0(name, this.name) && matches0(desc, this.desc);
	}

	private boolean matches0(String input, Pattern pattern) {
		return pattern == null || input == null || pattern.matcher(input).find();
	}

	public static class ParsingException extends Exception {
		public ParsingException(String message) {
			super(message);
		}

		public ParsingException(String message, Throwable cause) {
			super(message, cause);
		}

		public ParsingException(Throwable cause) {
			super(cause);
		}
	}
}
