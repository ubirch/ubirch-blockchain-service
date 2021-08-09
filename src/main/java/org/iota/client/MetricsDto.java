// Automatically generated by flapigen
package org.iota.client;


public final class MetricsDto {

    private MetricsDto() {}

    public final long newMessages() {
        long ret = do_newMessages(mNativeObj);

        return ret;
    }
    private static native long do_newMessages(long self);

    public final long receivedMessages() {
        long ret = do_receivedMessages(mNativeObj);

        return ret;
    }
    private static native long do_receivedMessages(long self);

    public final long knownMessages() {
        long ret = do_knownMessages(mNativeObj);

        return ret;
    }
    private static native long do_knownMessages(long self);

    public final long receivedMessageRequests() {
        long ret = do_receivedMessageRequests(mNativeObj);

        return ret;
    }
    private static native long do_receivedMessageRequests(long self);

    public final long receivedMilestoneRequests() {
        long ret = do_receivedMilestoneRequests(mNativeObj);

        return ret;
    }
    private static native long do_receivedMilestoneRequests(long self);

    public final long receivedHeartbeats() {
        long ret = do_receivedHeartbeats(mNativeObj);

        return ret;
    }
    private static native long do_receivedHeartbeats(long self);

    public final long sentMessages() {
        long ret = do_sentMessages(mNativeObj);

        return ret;
    }
    private static native long do_sentMessages(long self);

    public final long sentMessageRequests() {
        long ret = do_sentMessageRequests(mNativeObj);

        return ret;
    }
    private static native long do_sentMessageRequests(long self);

    public final long sentMilestoneRequests() {
        long ret = do_sentMilestoneRequests(mNativeObj);

        return ret;
    }
    private static native long do_sentMilestoneRequests(long self);

    public final long sentHeartbeats() {
        long ret = do_sentHeartbeats(mNativeObj);

        return ret;
    }
    private static native long do_sentHeartbeats(long self);

    public final long droppedPackets() {
        long ret = do_droppedPackets(mNativeObj);

        return ret;
    }
    private static native long do_droppedPackets(long self);

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
    /*package*/ MetricsDto(InternalPointerMarker marker, long ptr) {
        assert marker == InternalPointerMarker.RAW_PTR;
        this.mNativeObj = ptr;
    }
    /*package*/ long mNativeObj;
}