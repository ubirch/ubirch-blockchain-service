// Automatically generated by flapigen
package org.iota.client;


public final class SecretKey {
    @Override
    public String toString() {
        return this.to_string();
    }


    private SecretKey() {}

    private final String to_string() {
        String ret = do_to_string(mNativeObj);

        return ret;
    }
    private static native String do_to_string(long self);

    public static SecretKey generate() {
        long ret = do_generate();
        SecretKey convRet = new SecretKey(InternalPointerMarker.RAW_PTR, ret);

        return convRet;
    }
    private static native long do_generate();

    public static SecretKey fromLeBytes(byte [] bs) {
        long ret = do_fromLeBytes(bs);
        SecretKey convRet = new SecretKey(InternalPointerMarker.RAW_PTR, ret);

        return convRet;
    }
    private static native long do_fromLeBytes(byte [] bs);

    public final PublicKey publicKey() {
        long ret = do_publicKey(mNativeObj);
        PublicKey convRet = new PublicKey(InternalPointerMarker.RAW_PTR, ret);

        return convRet;
    }
    private static native long do_publicKey(long self);

    public final byte [] toLeBytes() {
        byte [] ret = do_toLeBytes(mNativeObj);

        return ret;
    }
    private static native byte [] do_toLeBytes(long self);

    public final Signature sign(byte [] msg) {
        long ret = do_sign(mNativeObj, msg);
        Signature convRet = new Signature(InternalPointerMarker.RAW_PTR, ret);

        return convRet;
    }
    private static native long do_sign(long self, byte [] msg);

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
    /*package*/ SecretKey(InternalPointerMarker marker, long ptr) {
        assert marker == InternalPointerMarker.RAW_PTR;
        this.mNativeObj = ptr;
    }
    /*package*/ long mNativeObj;
}