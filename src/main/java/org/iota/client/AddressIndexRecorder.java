// Automatically generated by flapigen
package org.iota.client;

/**
 * Structure for sorting of UnlockBlocks
 */
public final class AddressIndexRecorder {
    @Override
    public String toString() {
        return this.to_string();
    }


    private AddressIndexRecorder() {}

    private final String to_string() {
        String ret = do_to_string(mNativeObj);

        return ret;
    }
    private static native String do_to_string(long self);
    /**
     * Index of the account
     */
    public final long accountIndex() {
        long ret = do_accountIndex(mNativeObj);

        return ret;
    }
    private static native long do_accountIndex(long self);
    /**
     * The input used
     */
    public final Input input() {
        long ret = do_input(mNativeObj);
        Input convRet = new Input(InternalPointerMarker.RAW_PTR, ret);

        return convRet;
    }
    private static native long do_input(long self);
    /**
     * The output information
     */
    public final OutputResponse output() {
        long ret = do_output(mNativeObj);
        OutputResponse convRet = new OutputResponse(InternalPointerMarker.RAW_PTR, ret);

        return convRet;
    }
    private static native long do_output(long self);
    /**
     * index of this address on the seed
     */
    public final long addressIndex() {
        long ret = do_addressIndex(mNativeObj);

        return ret;
    }
    private static native long do_addressIndex(long self);
    /**
     * The chain derived from seed
     */
    public final Chain chain() {
        long ret = do_chain(mNativeObj);
        Chain convRet = new Chain(InternalPointerMarker.RAW_PTR, ret);

        return convRet;
    }
    private static native long do_chain(long self);
    /**
     * Whether this is an internal address
     */
    public final boolean internal() {
        boolean ret = do_internal(mNativeObj);

        return ret;
    }
    private static native boolean do_internal(long self);
    /**
     * The address
     */
    public final String bech32Address() {
        String ret = do_bech32Address(mNativeObj);

        return ret;
    }
    private static native String do_bech32Address(long self);

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
    /*package*/ AddressIndexRecorder(InternalPointerMarker marker, long ptr) {
        assert marker == InternalPointerMarker.RAW_PTR;
        this.mNativeObj = ptr;
    }
    /*package*/ long mNativeObj;
}