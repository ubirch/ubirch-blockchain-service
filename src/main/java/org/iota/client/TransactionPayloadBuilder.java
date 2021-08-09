// Automatically generated by flapigen
package org.iota.client;


public final class TransactionPayloadBuilder {
    /**
     * Creates a new `TransactionPayloadBuilder`.
     */
    public TransactionPayloadBuilder() {
        mNativeObj = init();
    }
    private static native long init();
    /**
     * Adds an essence to a `TransactionPayloadBuilder`.
     */
    public final TransactionPayloadBuilder withEssence(Essence essence) {
        long a0 = essence.mNativeObj;
        essence.mNativeObj = 0;

        long ret = do_withEssence(mNativeObj, a0);
        TransactionPayloadBuilder convRet = new TransactionPayloadBuilder(InternalPointerMarker.RAW_PTR, ret);

        JNIReachabilityFence.reachabilityFence1(essence);

        return convRet;
    }
    private static native long do_withEssence(long self, long essence);
    /**
     * Adds unlock blocks to a `TransactionPayloadBuilder`.
     */
    public final TransactionPayloadBuilder withUnlockBlocks(UnlockBlocks unlock_blocks) {
        long a0 = unlock_blocks.mNativeObj;
        unlock_blocks.mNativeObj = 0;

        long ret = do_withUnlockBlocks(mNativeObj, a0);
        TransactionPayloadBuilder convRet = new TransactionPayloadBuilder(InternalPointerMarker.RAW_PTR, ret);

        JNIReachabilityFence.reachabilityFence1(unlock_blocks);

        return convRet;
    }
    private static native long do_withUnlockBlocks(long self, long unlock_blocks);
    /**
     * Finishes a `TransactionPayloadBuilder` into a `TransactionPayload`.
     */
    public final TransactionPayload finish() {
        long ret = do_finish(mNativeObj);
        TransactionPayload convRet = new TransactionPayload(InternalPointerMarker.RAW_PTR, ret);

        return convRet;
    }
    private static native long do_finish(long self);

    public synchronized void delete() {
        if (mNativeObj != 0) {
            do_delete(mNativeObj);
            mNativeObj = 0;
       }
    }
    @Override
    protected void finalize() throws Throwable {
        try {
            delete();
        }
        finally {
             super.finalize();
        }
    }
    private static native void do_delete(long me);
    /*package*/ TransactionPayloadBuilder(InternalPointerMarker marker, long ptr) {
        assert marker == InternalPointerMarker.RAW_PTR;
        this.mNativeObj = ptr;
    }
    /*package*/ long mNativeObj;
}