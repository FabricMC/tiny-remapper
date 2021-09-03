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

package net.fabricmc.tinyremapper.extension.mixin.refmap;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Optional;

import net.fabricmc.tinyremapper.api.TrEnvironment;
import net.fabricmc.tinyremapper.extension.mixin.common.Logger;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Message;
import net.fabricmc.tinyremapper.extension.mixin.lib.gson.JsonReader;
import net.fabricmc.tinyremapper.extension.mixin.lib.gson.JsonWriter;
import net.fabricmc.tinyremapper.extension.mixin.soft.data.MemberInfo;
import net.fabricmc.tinyremapper.extension.mixin.soft.util.MemberInfoMappable;

public final class RefmapUtility {
	private final CommonData data;

	public RefmapUtility(TrEnvironment environment, Logger logger) {
		this.data = new CommonData(environment, logger);
	}

	public void process(Reader in, Writer out) {
		try {
			processRefmap(new JsonReader(in), new JsonWriter(out));
		} catch (Exception e) {
			data.logger.error(e.getMessage());
		}
	}

	private void processRefmap(JsonReader in, JsonWriter out) throws IOException {
		// {
		//   "mappings" : <mapping>,
		//   "data": <data>
		// }

		while (in.hasNext()) {
			String name = in.nextName();
			out.name(name);

			if (name.equals("mappings")) {
				processMapping(in, out);
			} else if (name.equals("data")) {
				processData(in, out);
			} else {
				throw new RuntimeException("Unknown entry \"" + name + "\" in refmap.");
			}
		}
	}

	private void processData(JsonReader in, JsonWriter out) throws IOException {
		// {
		//   "<namespace>" : <mapping>,
		//   "<namespace>" : <mapping>,
		//   ...
		// }

		in.beginObject();
		out.beginObject();

		while (in.hasNext()) {
			out.name(in.nextName());
			processMapping(in, out);
		}

		in.endObject();
		out.endObject();
	}

	private void processMapping(JsonReader in, JsonWriter out) throws IOException {
		// {
		//   "<class>" : <entry>,
		//   "<class>" : <entry>,
		//   ...
		// }

		in.beginObject();
		out.beginObject();

		while (in.hasNext()) {
			out.name(in.nextName());
			processEntry(in, out);
		}

		in.endObject();
		out.endArray();
	}

	private void processEntry(JsonReader in, JsonWriter out) throws IOException {
		// {
		//   "<member>" : "<translation>",
		//   "<member>" : "<translation>",
		//   ...
		// }
		in.beginObject();
		out.beginObject();

		while (in.hasNext()) {
			out.name(in.nextName());
			String translation = in.nextString();
			Optional<MemberInfo> info = Optional.ofNullable(MemberInfo.parse(translation));
			info = info.map(i -> new MemberInfoMappable(data, i).result());

			if (!info.isPresent()) {
				data.logger.warn(String.format(Message.INVALID_FORMAT, translation));
				continue;
			}

			out.value(info.toString());
		}

		in.endObject();
		out.endObject();
	}
}
