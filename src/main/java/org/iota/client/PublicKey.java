// Automatically generated by flapigen
package org.iota.client;


public final class PublicKey {
    @Override
    public String toString() {
        return this.to_string();
    }


    private PublicKey() {}

    private final String to_string() {
        String ret = do_to_string(mNativeObj);

        return ret;
    }
    private static native String do_to_string(long self);

    public final boolean verify(Signature sig, byte [] msg) {
        long a0 = sig.mNativeObj;
        sig.mNativeObj = 0;

        boolean ret = do_verify(mNativeObj, a0, msg);

        JNIReachabilityFence.reachabilityFence1(sig);

        return ret;
    }
    private static native boolean do_verify(long self, long sig, byte [] msg);

    public final byte [] toCompressedBytes() {
        byte [] ret = do_toCompressedBytes(mNativeObj);

        return ret;
    }
    private static native byte [] do_toCompressedBytes(long self);

    public static PublicKey fromCompressedBytes(byte [] bs) {
        long ret = do_fromCompressedBytes(bs);
        PublicKey convRet = new PublicKey(InternalPointerMarker.RAW_PTR, ret);

        return convRet;
    }
    private static native long do_fromCompressedBytes(byte [] bs);

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
    /*package*/ PublicKey(InternalPointerMarker marker, long ptr) {
        assert marker == InternalPointerMarker.RAW_PTR;
        this.mNativeObj = ptr;
    }
    /*package*/ long mNativeObj;
}