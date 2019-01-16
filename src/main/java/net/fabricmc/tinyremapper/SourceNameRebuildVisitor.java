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

import org.objectweb.asm.ClassVisitor;

public class SourceNameRebuildVisitor extends ClassVisitor {
    private String className;

    public SourceNameRebuildVisitor(int api, ClassVisitor classVisitor) {
        super(api, classVisitor);
    }

    public void visit(
            final int version,
            final int access,
            final String name,
            final String signature,
            final String superName,
            final String[] interfaces) {

        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);

        String filename = className.replace('.', '/');
        int startPos = filename.lastIndexOf('/') + 1;
        int endPos = filename.indexOf('$');
        if (endPos < 0) {
            endPos = filename.length();
        }
        if (endPos <= startPos) {
            System.err.println("SourceNameRebuildVisitor: Invalid class filename: " + filename + ", please fix!");
            super.visitSource(null, null);
        } else {
            super.visitSource(filename.substring(startPos, endPos) + ".java", null);
        }
    }

    @Override
    public void visitSource(final String source, final String debug) {
        // Already propagated above, do not call.
    }
}
