package net.fabricmc.tinyremapper.extension.mixin.data;

public enum AnnotationElement {
	VALUE("value"),
	REMAP("remap"),
	TARGET("target"),
	TARGETS("targets"),
	PREFIX("prefix"),
	IFACE("iface"),
	DESC("desc"),
	ARGS("args"),
	OWNER("owner"),
	RET("ret"),
	AT("at"),
	FROM("from"),
	TO("to"),
	SLICE("slice"),
	METHOD("method");

	private final String literal;

	AnnotationElement(String literal) {
		this.literal = literal;
	}

	public String get() {
		return literal;
	}
}
