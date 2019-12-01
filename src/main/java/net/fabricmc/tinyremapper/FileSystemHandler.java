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

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Reference counted fs handling.
 *
 * <p>The implementation closes file systems opened by itself once they are closed as often as they were opened. This
 * allows intersecting open+close actions on e.g. the zip based file systems. The caller has to ensure open and close
 * invocations are mirrored.
 */
public final class FileSystemHandler {
	public static synchronized FileSystem open(URI uri) throws IOException {
		FileSystem ret = null;

		try {
			ret = FileSystems.getFileSystem(uri);
		} catch (FileSystemNotFoundException e) { }

		boolean opened;

		if (ret == null) {
			ret = FileSystems.newFileSystem(uri, Collections.emptyMap());
			opened = true;
		} else {
			opened = false;
		}

		RefData data = fsRefs.get(ret);

		if (data == null) {
			fsRefs.put(ret, new RefData(opened, 1));
		} else {
			data.refs++;
		}

		return ret;
	}

	public static synchronized void close(FileSystem fs) throws IOException {
		RefData data = fsRefs.get(fs);
		if (data == null || data.refs <= 0) throw new IllegalStateException("fs "+fs+" never opened via FileSystemHandler");

		if (--data.refs == 0) {
			fsRefs.remove(fs);

			if (data.opened) fs.close();
		}
	}

	private static class RefData {
		public RefData(boolean opened, int refs) {
			this.opened = opened;
			this.refs = refs;
		}

		boolean opened; // fs opened by us -> close when refs get to 0
		int refs;
	}

	private static final Map<FileSystem, RefData> fsRefs = new IdentityHashMap<>();
}
