package net.fabricmc.tinyremapper.extension.mixin.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class SimpleMappingHolder implements IMappingHolder {
	Map<Member, String> methods = new HashMap<>();
	Map<Member, String> fields = new HashMap<>();

	public synchronized void putMethod(String owner, String srcName, String desc, String dstName) {
		methods.put(new Member(owner, srcName, desc), dstName);
	}

	public synchronized void putField(String owner, String srcName, String desc, String dstName) {
		fields.put(new Member(owner, srcName, desc), dstName);
	}

	@Override
	public void load(MappingAcceptor out) {
		for (Entry<Member, String> method : methods.entrySet()) {
			out.acceptMethod(method.getKey(), method.getValue());
		}

		for (Entry<Member, String> field : fields.entrySet()) {
			out.acceptField(field.getKey(), field.getValue());
		}
	}
}
