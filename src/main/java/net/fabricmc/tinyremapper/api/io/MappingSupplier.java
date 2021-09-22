package net.fabricmc.tinyremapper.api.io;

import java.io.IOException;

public interface MappingSupplier {
    String getSource();
    void load(MappingConsumer consumer) throws IOException;

    interface MappingConsumer {
        void acceptClass(String srcName, String dstName);
        void acceptMethod(String owner, String srcName, String desc, String dstName);
        void acceptMethodArg(String owner, String srcName, String desc, int lvIndex, String dstName);
        void acceptMethodVar(String owner, String srcName, String desc, int lvIndex, int startOpIdx, int asmIndex, String dstName);
        void acceptField(String owner, String srcName, String desc, String dstName);
    }
}
