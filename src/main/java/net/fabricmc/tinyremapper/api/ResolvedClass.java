package net.fabricmc.tinyremapper.api;

/**
 * A class that has been fully read
 */
public interface ResolvedClass extends ClassHeader {
	/**
	 * get field by name and descriptor, this is not cached
	 */
	MemberHeader getField(String name, String desc);

	/**
	 * get method by name and descriptor, this is not cached
	 */
	MemberHeader getMethod(String name, String desc);
}
