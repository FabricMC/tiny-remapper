package net.fabricmc.tinyremapper.extension.mixin.common.data;

public final class Message {
	public static final String CANNOT_RESOLVE_CLASS = "Cannot resolve class %s";
	public static final String CONFLICT_MAPPING = "Conflict mapping detected, %s -> %s.";
	public static final String NO_MAPPING_NON_RECURSIVE = "Cannot remap %s because it does not exists in any of the targets %s";
	public static final String NO_MAPPING_RECURSIVE = "Cannot remap %s because it does not exists in any of the targets %s or their parents.";
	public static final String NOT_FULLY_QUALIFIED = "%s is not fully qualified.";
}
