// Automatically generated by flapigen
package org.iota.client;

/**
 * Helper struct for offline signing
 */
public final class PreparedTransactionData {
    @Override
    public String toString() {
        return this.to_string();
    }


    private PreparedTransactionData() {}

    private final String to_string() {
        String ret = do_to_string(mNativeObj);

        return ret;
    }
    private static native String do_to_string(long self);
    /**
     * Turns a serialized preparedTransactionData string back into its class
     */
    public static PreparedTransactionData deserialize(String serialised_data) {
        long ret = do_deserialize(serialised_data);
        PreparedTransactionData convRet = new PreparedTransactionData(InternalPointerMarker.RAW_PTR, ret);

        return convRet;
    }
    private static native long do_deserialize(String serialised_data);
    /**
     * Transaction essence
     */
    public final Essence essence() {
        long ret = do_essence(mNativeObj);
        Essence convRet = new Essence(InternalPointerMarker.RAW_PTR, ret);

        return convRet;
    }
    private static native long do_essence(long self);
    /**
     * Required address information for signing
     */
    public final AddressIndexRecorder [] addressIndexRecorders() {
        AddressIndexRecorder [] ret = do_addressIndexRecorders(mNativeObj);

        return ret;
    }
    private static native AddressIndexRecorder [] do_addressIndexRecorders(long self);
    /**
     * Serializes the prepared data into a json string
     */
    public final String serialize() {
        String ret = do_serialize(mNativeObj);

        return ret;
    }
    private static native String do_serialize(long self);

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
    /*package*/ PreparedTransactionData(InternalPointerMarker marker, long ptr) {
        assert marker == InternalPointerMarker.RAW_PTR;
        this.mNativeObj = ptr;
    }
    /*package*/ long mNativeObj;
}