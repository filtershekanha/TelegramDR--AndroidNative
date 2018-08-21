package com.filtershekanha.teledr.tgnet;

public interface RequestDelegateInternal {
    void run(long response, int errorCode, String errorText, int networkType);
}
