// Automatically generated by flapigen
package org.iota.client;


public final class MigratedFundsEntryDto {

    private MigratedFundsEntryDto() {}

    public final String tail_transaction_hash() {
        String ret = do_tail_transaction_hash(mNativeObj);

        return ret;
    }
    private static native String do_tail_transaction_hash(long self);

    public final AddressDto address() {
        long ret = do_address(mNativeObj);
        AddressDto convRet = new AddressDto(InternalPointerMarker.RAW_PTR, ret);

        return convRet;
    }
    private static native long do_address(long self);

    public final long deposit() {
        long ret = do_deposit(mNativeObj);

        return ret;
    }
    private static native long do_deposit(long self);

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
    /*package*/ MigratedFundsEntryDto(InternalPointerMarker marker, long ptr) {
        assert marker == InternalPointerMarker.RAW_PTR;
        this.mNativeObj = ptr;
    }
    /*package*/ long mNativeObj;
}