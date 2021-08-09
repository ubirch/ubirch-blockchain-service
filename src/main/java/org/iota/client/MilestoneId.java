// Automatically generated by flapigen
package org.iota.client;


public final class MilestoneId {
    @Override
    public String toString() {
        return this.to_string();
    }


    private MilestoneId() {}

    private final String to_string() {
        String ret = do_to_string(mNativeObj);

        return ret;
    }
    private static native String do_to_string(long self);

    public static MilestoneId fromString(String str_rep) {
        long ret = do_fromString(str_rep);
        MilestoneId convRet = new MilestoneId(InternalPointerMarker.RAW_PTR, ret);

        return convRet;
    }
    private static native long do_fromString(String str_rep);

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
    /*package*/ MilestoneId(InternalPointerMarker marker, long ptr) {
        assert marker == InternalPointerMarker.RAW_PTR;
        this.mNativeObj = ptr;
    }
    /*package*/ long mNativeObj;
}