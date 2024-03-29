// Automatically generated by flapigen
package org.iota.client;

/**
 * A builder to build a `Message`.
 */
public final class MessageBuilder {
    /**
     * Creates a new `MessageBuilder`.
     */
    public MessageBuilder() {
        mNativeObj = init();
    }
    private static native long init();
    /**
     * Adds a network id to a `MessageBuilder`.
     */
    public final MessageBuilder networkId(long network_id) {
        long ret = do_networkId(mNativeObj, network_id);
        MessageBuilder convRet = new MessageBuilder(InternalPointerMarker.RAW_PTR, ret);

        return convRet;
    }
    private static native long do_networkId(long self, long network_id);
    /**
     * Adds parents to a `MessageBuilder`.
     */
    public final MessageBuilder parents(MessageId[] parents) {
        long ret = do_parents(mNativeObj, parents);
        MessageBuilder convRet = new MessageBuilder(InternalPointerMarker.RAW_PTR, ret);

        return convRet;
    }
    private static native long do_parents(long self, MessageId[] parents);
    /**
     * Adds a payload to a `MessageBuilder`.
     */
    public final MessageBuilder payload(MessagePayload payload) {
        long a0 = payload.mNativeObj;
        payload.mNativeObj = 0;

        long ret = do_payload(mNativeObj, a0);
        MessageBuilder convRet = new MessageBuilder(InternalPointerMarker.RAW_PTR, ret);

        JNIReachabilityFence.reachabilityFence1(payload);

        return convRet;
    }
    private static native long do_payload(long self, long payload);

    public final Message finish() {
        long ret = do_finish(mNativeObj);
        Message convRet = new Message(InternalPointerMarker.RAW_PTR, ret);

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
    /*package*/ MessageBuilder(InternalPointerMarker marker, long ptr) {
        assert marker == InternalPointerMarker.RAW_PTR;
        this.mNativeObj = ptr;
    }
    /*package*/ long mNativeObj;
}