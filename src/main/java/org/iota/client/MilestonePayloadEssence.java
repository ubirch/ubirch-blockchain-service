// Automatically generated by flapigen
package org.iota.client;


public final class MilestonePayloadEssence {
    @Override
    public String toString() {
        return this.to_string();
    }


    private MilestonePayloadEssence() {}

    private final String to_string() {
        String ret = do_to_string(mNativeObj);

        return ret;
    }
    private static native String do_to_string(long self);
    /**
     * Returns the index of a `MilestonePayloadEssence`.
     */
    public final long index() {
        long ret = do_index(mNativeObj);

        return ret;
    }
    private static native long do_index(long self);
    /**
     * Returns the timestamp of a `MilestonePayloadEssence`.
     */
    public final long timestamp() {
        long ret = do_timestamp(mNativeObj);

        return ret;
    }
    private static native long do_timestamp(long self);
    /**
     * Returns the parents of a `MilestonePayloadEssence`.
     */
    public final MessageId [] parents() {
        MessageId [] ret = do_parents(mNativeObj);

        return ret;
    }
    private static native MessageId [] do_parents(long self);
    /**
     * Returns the merkle proof of a `MilestonePayloadEssence`.
     */
    public final byte [] merkleProof() {
        byte [] ret = do_merkleProof(mNativeObj);

        return ret;
    }
    private static native byte [] do_merkleProof(long self);
    /**
     * Returns the next proof of work score of a `MilestonePayloadEssence`.
     */
    public final long nextPowScore() {
        long ret = do_nextPowScore(mNativeObj);

        return ret;
    }
    private static native long do_nextPowScore(long self);
    /**
     * Returns the next proof of work index of a `MilestonePayloadEssence`.
     */
    public final long nextPowScoreMilestone() {
        long ret = do_nextPowScoreMilestone(mNativeObj);

        return ret;
    }
    private static native long do_nextPowScoreMilestone(long self);
    /**
     * Returns the public keys of a `MilestonePayloadEssence`.
     */
    public final PublicKey [] publicKeys() {
        PublicKey [] ret = do_publicKeys(mNativeObj);

        return ret;
    }
    private static native PublicKey [] do_publicKeys(long self);
    /**
     * Returns the optional receipt of a `MilestonePayloadEssence`.
     */
    public final java.util.Optional<ReceiptPayload> receipt() {
        long ret = do_receipt(mNativeObj);
        java.util.Optional<ReceiptPayload> convRet;
        if (ret != 0) {
            convRet = java.util.Optional.of(new ReceiptPayload(InternalPointerMarker.RAW_PTR, ret));
        } else {
            convRet = java.util.Optional.empty();
        }

        return convRet;
    }
    private static native long do_receipt(long self);
    /**
     * Hashes the `MilestonePayloadEssence to be signed.`
     */
    public final byte [] hash() {
        byte [] ret = do_hash(mNativeObj);

        return ret;
    }
    private static native byte [] do_hash(long self);

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
    /*package*/ MilestonePayloadEssence(InternalPointerMarker marker, long ptr) {
        assert marker == InternalPointerMarker.RAW_PTR;
        this.mNativeObj = ptr;
    }
    /*package*/ long mNativeObj;
}