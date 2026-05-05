package crow.client.notifications;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class NotificationManager {

    private static final int MAX_ACTIVE = 4;

    private static final LinkedBlockingQueue<Notification> pending = new LinkedBlockingQueue<>();
    private static final List<Notification> active = new ArrayList<>();

    public static void show(Notification notification) {
        pending.add(notification);
    }

    public static void show(NotificationType type, String title, String message, int length) {
        show(new Notification(type, title, message, length));
    }

    public static void render() {

        Iterator<Notification> it = active.iterator();
        while (it.hasNext()) {
            if (!it.next().isShown()) it.remove();
        }

        while (active.size() < MAX_ACTIVE && !pending.isEmpty()) {
            Notification n = pending.poll();
            if (n != null) {
                n.show();
                active.add(n);
            }
        }

        for (int i = 0; i < active.size(); i++) {
            active.get(i).render(i);
        }
    }
}
