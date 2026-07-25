package crow.client.utils;

/**
 * Marks packets that are being replayed by a delay module. Network sends are
 * synchronous, so a thread-local depth lets every delay owner ignore replayed
 * packets without weakening handling for unrelated threads.
 */
public final class PacketReplayGuard {

    private static final ThreadLocal<Integer> REPLAY_DEPTH = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    private PacketReplayGuard() {
    }

    public static boolean isReplaying() {
        return REPLAY_DEPTH.get() > 0;
    }

    public static void runGuarded(Runnable replayAction) {
        int previousDepth = REPLAY_DEPTH.get();
        REPLAY_DEPTH.set(previousDepth + 1);
        try {
            replayAction.run();
        } finally {
            if (previousDepth == 0) {
                REPLAY_DEPTH.remove();
            } else {
                REPLAY_DEPTH.set(previousDepth);
            }
        }
    }
}
