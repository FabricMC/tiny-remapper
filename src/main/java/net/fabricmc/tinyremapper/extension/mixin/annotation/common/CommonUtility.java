package net.fabricmc.tinyremapper.extension.mixin.annotation.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.commons.Remapper;

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrEnvironment;
import net.fabricmc.tinyremapper.api.TrMember;
import net.fabricmc.tinyremapper.api.TrMember.MemberType;
import net.fabricmc.tinyremapper.extension.mixin.common.Logger;
import net.fabricmc.tinyremapper.extension.mixin.data.AnnotationType;
import net.fabricmc.tinyremapper.extension.mixin.data.IMappingHolder;
import net.fabricmc.tinyremapper.extension.mixin.data.MemberInfo;

public final class CommonUtility {
	public static String removeCamelPrefix(String prefix, String str) {
		if (str.startsWith(prefix)) {
			str = str.substring(prefix.length());

			return str.isEmpty() ? str
					: str.substring(0, 1).toLowerCase(Locale.ROOT) + str.substring(1);
		}

		throw new RuntimeException(prefix + " is not the prefix of " + str);
	}

	public static String addCamelPrefix(String prefix, String str) {
		return str.isEmpty() ? prefix
				: prefix + str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1);
	}

	public static void emit(Remapper remapper, AnnotationType type,
							IMappingHolder mapping, String owner,
							String srcName, String srcDesc, String dstName) {
		if (srcName.equals(dstName)) {
			throw new RuntimeException("srcName and dstName are the same, " + srcName);
		} else {
			// srcDesc need to be remapped, at it may be remapped by tiny-remapper in
			// this pass.

			if (type.equals(AnnotationType.METHOD)) {
				String desc = remapper.mapMethodDesc(srcDesc);
				mapping.putMethod(owner, srcName, desc, dstName);
			} else if (type.equals(AnnotationType.FIELD)) {
				String desc = remapper.mapDesc(srcDesc);
				mapping.putField(owner, srcName, desc, dstName);
			} else {
				throw new RuntimeException("Encounter non-member type " + type.name());
			}
		}
	}

	public static String remap(Remapper remapper, AnnotationType type,
							String owner, String srcName, String srcDesc) {
		if (type.equals(AnnotationType.METHOD)) {
			return remapper.mapMethodName(owner, srcName, srcDesc);
		} else if (type.equals(AnnotationType.FIELD)) {
			return remapper.mapFieldName(owner, srcName, srcDesc);
		} else {
			throw new RuntimeException("Encounter non-member type " + type.name());
		}
	}

	public static String remap(Remapper remapper, AnnotationType type,
									List<String> owners, String srcName, String srcDesc) {
		String dstName = null;

		for (String owner : owners) {
			String tmp = remap(remapper, type, owner, srcName, srcDesc);

			if (!srcName.equals(tmp)) {
				if (dstName == null) {
					dstName = tmp;
				} else if (dstName.equals(tmp)) {
					// they are the same name, ignore
				} else {
					Logger.error("Detect conflict mapping " + srcName + " -> " + dstName + "; "
							+ srcName + " -> " + tmp + ". This is a serious issue!");
					return srcName;
				}
			}
		}

		return dstName == null ? srcName : dstName;
	}

	public static String classNameToDesc(String className) {
		if (!MemberInfo.isClass(className)) {
			throw new RuntimeException("Invalid class name format" + className);
		}

		return "L" + className + ";";
	}

	public static String classDescToName(String classDesc) {
		if (!classDesc.startsWith("L") || !classDesc.endsWith(";")) {
			throw new RuntimeException("Invalid class desc format " + classDesc);
		}

		return classDesc.substring(1, classDesc.length() - 1);
	}

	public static MemberInfo remap(Remapper remapper, TrEnvironment environment,
								String target, MemberInfo srcInfo) {
		if (srcInfo.type != null && srcInfo.type.equals(AnnotationType.CLASS)) {
			return new MemberInfo(AnnotationType.CLASS, "",
					remapper.map(srcInfo.name), "", "");
		} else {
			AnnotationType type = srcInfo.type;
			String srcOwner = srcInfo.owner.isEmpty() ? target : classDescToName(srcInfo.owner);
			String srcName = srcInfo.name;
			String srcDesc = srcInfo.desc;

			if (srcDesc.isEmpty()) {    // if desc is empty, then so does type
				TrClass _class = environment.getClass(srcOwner);
				Collection<TrMember> members = new ArrayList<>();

				// collect both methods and fields
				_class.resolveFields(srcName, members);
				_class.resolveMethods(srcName, members);

				Stream<TrMember> normalStream = members.stream().filter(m -> !m.isSynthetic());
				Stream<TrMember> syntheticStream = members.stream().filter(TrMember::isSynthetic);

				if (normalStream.count() > 1) {
					Logger.error("Ambiguous target member " + normalStream.map(m -> m.getName() + m.getDesc()).collect(Collectors.joining(", "))
							+ ". Please specify descriptor");
					return srcInfo;
				} else if (normalStream.count() == 1) {
					TrMember member = Objects.requireNonNull(normalStream.findAny().orElse(null));
					srcDesc = member.getDesc();
					type = member.getType().equals(MemberType.FIELD) ? AnnotationType.FIELD : AnnotationType.METHOD;
				} else if (syntheticStream.count() > 1) {
					Logger.error("Ambiguous target member " + syntheticStream.map(m -> m.getName() + m.getDesc()).collect(Collectors.joining(", "))
							+ ". Please specify descriptor");
					return srcInfo;
				} else if (syntheticStream.count() == 1) {
					TrMember member = Objects.requireNonNull(syntheticStream.findAny().orElse(null));
					srcDesc = member.getDesc();
					type = member.getType().equals(MemberType.FIELD) ? AnnotationType.FIELD : AnnotationType.METHOD;
				} else {
					return srcInfo;
				}
			}

			String dstOwner = remapper.map(srcOwner);
			String dstName = remap(remapper, type, srcOwner, srcName, srcDesc);
			String dstDesc = type.equals(AnnotationType.FIELD) ? remapper.mapDesc(srcDesc) : remapper.mapMethodDesc(srcDesc);

			return new MemberInfo(srcInfo.type,
					srcInfo.owner.isEmpty() ? "" : classNameToDesc(dstOwner),
					dstName, srcInfo.quantifier, dstDesc);
		}
	}

	public static MemberInfo remap(Remapper remapper, TrEnvironment environment,
								List<String> targets, MemberInfo srcInfo) {
		MemberInfo dstInfo = null;

		for (String target : targets) {
			MemberInfo tmp = remap(remapper, environment, target, srcInfo);

			if (!srcInfo.name.equals(tmp.name)) {
				if (dstInfo == null) {
					dstInfo = tmp;
				} else if (dstInfo.name.equals(tmp.name) && dstInfo.desc.equals(tmp.desc)) {
					// they are the same name, ignore
				} else {
					Logger.error("Detect conflict mapping " + srcInfo.name + srcInfo.desc
							+ " -> " + dstInfo.name + dstInfo.desc + "; "
							+ srcInfo.name + srcInfo.desc
							+ " -> " + tmp.name + tmp.desc + ".");
					return srcInfo;
				}
			}
		}

		if (dstInfo != null) {
			return dstInfo;
		}

		// otherwise, at least try to remap owner and desc

		String owner = srcInfo.owner;
		String desc = srcInfo.desc;

		if (!owner.isEmpty()) {
			owner = remapper.mapDesc(owner);	// remap owner, notice owner is descriptor
		}

		if (!desc.isEmpty()) {	// desc is not empty, then type must be one of method or field
			desc = srcInfo.type.equals(AnnotationType.FIELD)	// remap desc
					? remapper.mapDesc(desc) : remapper.mapMethodDesc(desc);
		}

		return new MemberInfo(srcInfo.type, owner, srcInfo.name, srcInfo.quantifier, desc);
	}
}
