package crow.client.command.commands;

import crow.client.command.Command;
import crow.client.main.Crow;

import static crow.client.clickgui.crow.Terminal.print;

public class Uwu extends Command {
    private static boolean u;

    public Uwu() {
        super("uwu", "hevex/blowsy added this lol", 0, 0, new String[] {},
                new String[] { "hevex", "blowsy", "weeb", "torture", "noplsno" });
        u = false;
    }

    @Override
    public void onCall(String[] args) {
        if (u) {
            return;
        }

        Crow.getExecutor().execute(() -> {
            u = true;

            for (int i = 0; i < 4; ++i) {
                if (i == 0) {
                    print("nya");
                } else if (i == 1) {
                    print("ichi ni san");
                } else if (i == 2) {
                    print("nya");
                } else {
                    print("arigatou!");
                }

                try {
                    Thread.sleep(500L);
                } catch (InterruptedException var2) {
                }
            }

            u = false;
        });
    }
}
