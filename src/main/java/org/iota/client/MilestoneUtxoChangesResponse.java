// Automatically generated by flapigen
package org.iota.client;


public final class MilestoneUtxoChangesResponse {
    @Override
    public String toString() {
        return this.to_string();
    }

    public boolean equals(Object obj) {
        boolean equal = false;
        if (obj instanceof MilestoneUtxoChangesResponse)
        equal = ((MilestoneUtxoChangesResponse)obj).rustEq(this);
        return equal;
    }

    public int hashCode() {
        return (int)mNativeObj;
    }


    private MilestoneUtxoChangesResponse() {}

    private final String to_string() {
        String ret = do_to_string(mNativeObj);

        return ret;
    }
    private static native String do_to_string(long self);

    private final boolean rustEq(MilestoneUtxoChangesResponse o) {
        long a0 = o.mNativeObj;
        boolean ret = do_rustEq(mNativeObj, a0);

        JNIReachabilityFence.reachabilityFence1(o);

        return ret;
    }
    private static native boolean do_rustEq(long self, long o);
    /**
     * Milestone index.
     */
    public final long index() {
        long ret = do_index(mNativeObj);

        return ret;
    }
    private static native long do_index(long self);
    /**
     * Milestone message id
     */
    public final java.lang.String [] createdOutputs() {
        java.lang.String [] ret = do_createdOutputs(mNativeObj);

        return ret;
    }
    private static native java.lang.String [] do_createdOutputs(long self);
    /**
     * Milestone timestamp.
     */
    public final java.lang.String [] consumedOutputs() {
        java.lang.String [] ret = do_consumedOutputs(mNativeObj);

        return ret;
    }
    private static native java.lang.String [] do_consumedOutputs(long self);

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
    /*package*/ MilestoneUtxoChangesResponse(InternalPointerMarker marker, long ptr) {
        assert marker == InternalPointerMarker.RAW_PTR;
        this.mNativeObj = ptr;
    }
    /*package*/ long mNativeObj;
}