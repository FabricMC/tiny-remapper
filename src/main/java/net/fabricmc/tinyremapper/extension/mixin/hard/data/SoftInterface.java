package net.fabricmc.tinyremapper.extension.mixin.hard.data;

public final class SoftInterface {
	public enum Remap {
		NONE, ONLY_PREFIX, ALL, FORCE
	}

	private String target;
	private String prefix;
	private Remap remap;

	public String getTarget() {
		return target;
	}

	public String getPrefix() {
		return prefix;
	}

	public Remap getRemap() {
		return remap;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public void setRemap(Remap remap) {
		this.remap = remap;
	}

	@Override
	public String toString() {
		return "Interface{"
				+ "target='" + target + '\''
				+ ", prefix='" + prefix + '\''
				+ ", remap=" + remap
				+ '}';
	}
}
