package crow.client.gui;

import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.Session;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.*;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class AltManagerGui extends GuiScreen {

    private static final int    PANEL_W         = 310;
    private static final int    PANEL_H         = 220;
    private static final int    TAB_H           = 26;
    private static final String CLIENT_ID       = "aa2e34eb-a5d5-4835-8b1d-dde25d766e09";

    private static final String MS_DEVICE_URL   = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String MS_TOKEN_URL    = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_URL         = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL        = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_AUTH_URL     = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL  = "https://api.minecraftservices.com/minecraft/profile";

    private final GuiScreen parent;

    private int tab = 0;

    private final CTextField inputField = new CTextField(512);

    private volatile int     msState      = 0;
    private volatile String  msUserCode   = "";
    private volatile String  msVerifyUrl  = "microsoft.com/devicelogin";
    private volatile String  msDeviceCode = "";
    private volatile boolean msCancelled  = false;
    private float copyCodeAnim = 0F;
    private long  copyCodeFlash = 0;

    private volatile String  status      = "";
    private volatile int     statusColor = 0xFFAAAAAA;
    private volatile boolean loading     = false;

    private float[] tabAnim      = {0F, 0F, 0F};
    private float   loginAnim    = 0F;
    private float   backAnim     = 0F;
    private float   cancelAnim   = 0F;
    private float   importAnim   = 0F;
    private long    codeShowTime = 0;

    public AltManagerGui(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        inputField.clear();
        status = "";
    }

    @Override
    public void onGuiClosed() { cancelMicrosoft(); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawGradientRect(0, 0, width, height, 0xCC090B11, 0xCC121722);

        int px = width / 2 - PANEL_W / 2;
        int py = height / 2 - PANEL_H / 2;

        RenderUtils.drawRoundedRectAA(px - 6, py + 6, px + PANEL_W + 6, py + PANEL_H + 6, 16, 0x22000000);
        RenderUtils.drawRoundedRectAA(px, py, px + PANEL_W, py + PANEL_H, 16, 0xF01A1F28);
        RenderUtils.drawRoundedRectAA(px + 1, py + 1, px + PANEL_W - 1, py + PANEL_H - 1, 15, 0xF7242A34);

        String title = "Alt Manager";
        drawSemiBold(title, px + (PANEL_W - sw(title)) / 2.0F, py + 12, 0xFFFFFFFF);
        RenderUtils.drawFlowingGradientRect(px + 18, py + 30, px + PANEL_W - 18, py + 33, 110, 0);

        int gap  = 5;
        int tabW = (PANEL_W - 32 - gap * 2) / 3;
        int[] tx = {px + 16, px + 16 + tabW + gap, px + 16 + (tabW + gap) * 2};
        int   ty = py + 40;
        String[] tl = {"Offline", "Token", "Microsoft"};
        for (int i = 0; i < 3; i++) {
            boolean hov = over(mouseX, mouseY, tx[i], ty, tabW, TAB_H);
            tabAnim[i] += ((tab == i || hov ? 1F : 0F) - tabAnim[i]) * 0.22F;
            RenderUtils.drawRoundedRectAA(tx[i], ty, tx[i] + tabW, ty + TAB_H, 7,
                    blend(0xFF1E222C, 0xFF2A3040, tabAnim[i]));
            if (tab == i) RenderUtils.drawFlowingGradientRect(
                    tx[i] + 2, ty + TAB_H - 3, tx[i] + tabW - 2, ty + TAB_H - 1, 120, 0);
            drawSmall(tl[i], tx[i] + (tabW - smallW(tl[i])) / 2.0F, ty + 9,
                    tab == i ? 0xFFFFFFFF : 0xFF8899AA);
        }

        int contentTop = ty + TAB_H + 10;

        if (tab == 0 || tab == 1) drawInputTab(px, py, contentTop, mouseX, mouseY);
        else                       drawMicrosoftTab(px, py, contentTop, mouseX, mouseY);

        int bw = 72, bx = px + (PANEL_W - bw) / 2, by = py + PANEL_H - 30;
        boolean hovB = over(mouseX, mouseY, bx, by, bw, 22);
        backAnim += ((hovB ? 1F : 0F) - backAnim) * 0.22F;
        RenderUtils.drawRoundedRectAA(bx, by, bx + bw, by + 22, 8, blend(0xFF1A1F28, 0xFF252B38, backAnim));
        RenderUtils.drawRoundedOutline(bx, by, bx + bw, by + 22, 8, 1F, 0x16000000);
        drawSmall("Back", bx + (bw - smallW("Back")) / 2.0F, by + 7, hovB ? 0xFFFFFFFF : 0xFF8899AA);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawInputTab(int px, int py, int top, int mx, int my) {
        String hint = tab == 0 ? "Username" : "Minecraft Access Token";
        drawSmall(hint, px + 16, top - 2, 0xFF8899AA);

        int fx = px + 16, fy = top + 10, fw = PANEL_W - 32, fh = 26;
        inputField.draw(fx, fy, fw, fh, mx, my);

        int bw = PANEL_W - 32, bx = px + 16, by = top + 46;
        boolean hov = over(mx, my, bx, by, bw, 28);
        loginAnim += ((hov ? 1F : 0F) - loginAnim) * 0.22F;
        RenderUtils.drawRoundedRectAA(bx, by, bx + bw, by + 28, 10, blend(0xFF252B38, 0xFF313845, loginAnim));
        RenderUtils.drawRoundedOutline(bx, by, bx + bw, by + 28, 10, 1F, hov ? 0x44FFFFFF : 0x16000000);
        RenderUtils.drawFlowingGradientRect(bx + 2, by + 25, bx + bw - 2, by + 27, hov ? 150 : 80, 0);
        String bl = loading ? "..." : (tab == 0 ? "Login Offline" : "Login with Token");
        drawSemiBold(bl, bx + (bw - sw(bl)) / 2.0F, by + 9, loading ? 0xFF8899AA : (hov ? 0xFFFFFFFF : 0xFFDDE3EE));

        if (!status.isEmpty()) drawSmall(status, px + (PANEL_W - smallW(status)) / 2.0F, by + 36, statusColor);

        if (tab == 1) {
            int ibw = PANEL_W - 32, ibx = px + 16, iby = by + 48;
            boolean hovI = over(mx, my, ibx, iby, ibw, 22);
            importAnim += ((hovI ? 1F : 0F) - importAnim) * 0.22F;
            RenderUtils.drawRoundedRectAA(ibx, iby, ibx + ibw, iby + 22, 8,
                    blend(0xFF1A1F2A, 0xFF252B38, importAnim));
            RenderUtils.drawRoundedOutline(ibx, iby, ibx + ibw, iby + 22, 8, 1F,
                    hovI ? 0x33AACCFF : 0x14000000);
            String il = "Import token from Minecraft Launcher";
            drawSmall(il, ibx + (ibw - smallW(il)) / 2.0F, iby + 7,
                    hovI ? 0xFFCCDDFF : 0xFF667788);
        }
    }

    private void drawMicrosoftTab(int px, int py, int top, int mx, int my) {
        if (msState == 0) {
            String desc = "Sign in with your Microsoft account";
            drawSmall(desc, px + (PANEL_W - smallW(desc)) / 2.0F, top + 4, 0xFF8899AA);
            int bw = PANEL_W - 32, bx = px + 16, by = top + 22;
            boolean hov = over(mx, my, bx, by, bw, 30);
            loginAnim += ((hov ? 1F : 0F) - loginAnim) * 0.22F;
            RenderUtils.drawRoundedRectAA(bx, by, bx + bw, by + 30, 10, blend(0xFF252B38, 0xFF313845, loginAnim));
            RenderUtils.drawRoundedOutline(bx, by, bx + bw, by + 30, 10, 1F, hov ? 0x44FFFFFF : 0x16000000);
            RenderUtils.drawFlowingGradientRect(bx + 2, by + 27, bx + bw - 2, by + 29, hov ? 150 : 80, 0);
            String bl = "Sign in with Microsoft";
            drawSemiBold(bl, bx + (bw - sw(bl)) / 2.0F, by + 10, hov ? 0xFFFFFFFF : 0xFFDDE3EE);
            if (!status.isEmpty()) drawSmall(status, px + (PANEL_W - smallW(status)) / 2.0F, by + 40, statusColor);

        } else if (msState == 1) {
            String s = "Requesting login code...";
            drawSmall(s, px + (PANEL_W - smallW(s)) / 2.0F, top + 20, 0xFFAAAAAA);

        } else if (msState == 2) {

            drawSmall("Open your browser and go to:",
                    px + (PANEL_W - smallW("Open your browser and go to:")) / 2.0F, top + 2, 0xFF8899AA);
            int ubw = PANEL_W - 32, ubx = px + 16, uby = top + 14;
            RenderUtils.drawRoundedRectAA(ubx, uby, ubx + ubw, uby + 18, 6, 0xFF161B24);
            drawSmall(msVerifyUrl, ubx + (ubw - smallW(msVerifyUrl)) / 2.0F, uby + 4, 0xFFCCDDFF);

            drawSmall("Enter the code:",
                    px + (PANEL_W - smallW("Enter the code:")) / 2.0F, uby + 24, 0xFF8899AA);

            float pulse = (float)(Math.sin((System.currentTimeMillis() - codeShowTime) / 700.0) * 0.12 + 0.88);
            int codeX = px + PANEL_W / 2 - sw(msUserCode) / 2 - 28;
            drawSemiBold(msUserCode, codeX, uby + 36, ((int)(255 * pulse) << 24) | 0xFFFFFF);

            boolean copiedRecently = System.currentTimeMillis() - copyCodeFlash < 1200;
            String copyLabel = copiedRecently ? "Copied!" : "Copy";
            int cpW = smallW(copyLabel) + 14, cpH = 18;
            int cpX = px + PANEL_W / 2 + sw(msUserCode) / 2 + 6;
            int cpY = uby + 36;
            boolean hovCopy = over(mx, my, cpX, cpY, cpW, cpH);
            copyCodeAnim += ((hovCopy ? 1F : 0F) - copyCodeAnim) * 0.22F;
            RenderUtils.drawRoundedRectAA(cpX, cpY, cpX + cpW, cpY + cpH, 5,
                    blend(0xFF1A2030, 0xFF253040, copyCodeAnim));
            RenderUtils.drawRoundedOutline(cpX, cpY, cpX + cpW, cpY + cpH, 5, 1F,
                    copiedRecently ? 0x4455EE55 : (hovCopy ? 0x33AACCFF : 0x16000000));
            drawSmall(copyLabel, cpX + (cpW - smallW(copyLabel)) / 2.0F, cpY + 5,
                    copiedRecently ? 0xFF55EE55 : (hovCopy ? 0xFFCCDDFF : 0xFF778899));

            int dots = (int)((System.currentTimeMillis() / 500) % 4);
            String w = "Waiting" + "...".substring(0, dots);
            drawSmall(w, px + (PANEL_W - smallW(w)) / 2.0F, uby + 58, 0xFF666677);

            int cw = 90, cx2 = px + (PANEL_W - cw) / 2, cy = uby + 72;
            boolean hov = over(mx, my, cx2, cy, cw, 22);
            cancelAnim += ((hov ? 1F : 0F) - cancelAnim) * 0.22F;
            RenderUtils.drawRoundedRectAA(cx2, cy, cx2 + cw, cy + 22, 8, blend(0xFF1E1A24, 0xFF2E2030, cancelAnim));
            RenderUtils.drawRoundedOutline(cx2, cy, cx2 + cw, cy + 22, 8, 1F, hov ? 0x33FF4444 : 0x16000000);
            drawSmall("Cancel", cx2 + (cw - smallW("Cancel")) / 2.0F, cy + 7, hov ? 0xFFFF7777 : 0xFF997799);

        } else if (msState == 3) {
            String s = "Authenticating with Minecraft...";
            drawSmall(s, px + (PANEL_W - smallW(s)) / 2.0F, top + 20, 0xFFAAAAAA);

        } else {
            drawSmall(status, px + (PANEL_W - smallW(status)) / 2.0F, top + 20, statusColor);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int px = width / 2 - PANEL_W / 2;
        int py = height / 2 - PANEL_H / 2;

        int gap  = 5, tabW = (PANEL_W - 32 - gap * 2) / 3;
        int[] tx = {px + 16, px + 16 + tabW + gap, px + 16 + (tabW + gap) * 2};
        int   ty = py + 40;
        for (int i = 0; i < 3; i++) {
            if (over(mouseX, mouseY, tx[i], ty, tabW, TAB_H) && tab != i) {
                cancelMicrosoft();
                tab = i; inputField.clear(); inputField.setFocused(i != 2);
                status = ""; msState = 0; return;
            }
        }

        int contentTop = ty + TAB_H + 10;

        if (tab == 0 || tab == 1) {
            int fx = px + 16, fy = contentTop + 10, fw = PANEL_W - 32, fh = 26;
            inputField.mouseClicked(mouseX, mouseY, fx, fy, fw, fh);

            int bw = PANEL_W - 32, bx = px + 16, by = contentTop + 46;
            if (over(mouseX, mouseY, bx, by, bw, 28)) handleInputLogin();

            if (tab == 1) {
                int iby = by + 48;
                if (over(mouseX, mouseY, bx, iby, bw, 22)) importFromLauncher();
            }

        } else {
            if (msState == 0) {
                int bw = PANEL_W - 32, bx = px + 16, by = contentTop + 22;
                if (over(mouseX, mouseY, bx, by, bw, 30)) startMicrosoftLogin();
            } else if (msState == 2) {
                int uby = contentTop + 14;

                int cpW = smallW("Copy") + 14, cpH = 18;
                int cpX = px + PANEL_W / 2 + sw(msUserCode) / 2 + 6;
                int cpY = uby + 36;
                if (over(mouseX, mouseY, cpX, cpY, cpW, cpH)) {
                    copyText(msUserCode);
                    copyCodeFlash = System.currentTimeMillis();
                }

                int cw = 90, cx2 = px + (PANEL_W - cw) / 2, cy = uby + 72;
                if (over(mouseX, mouseY, cx2, cy, cw, 22)) {
                    cancelMicrosoft(); status = "Cancelled."; statusColor = 0xFFAAAAAA; msState = 0;
                }
            }
        }

        int bw = 72, bx = px + (PANEL_W - bw) / 2, by = py + PANEL_H - 30;
        if (over(mouseX, mouseY, bx, by, bw, 22)) mc.displayGuiScreen(parent);

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(parent); return; }
        if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) && tab != 2) {
            handleInputLogin(); return;
        }
        if (tab != 2) inputField.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() { inputField.tick(); }

    private void handleInputLogin() {
        if (loading) return;
        String v = inputField.getText().trim();
        if (v.isEmpty()) { status = "Please enter a value."; statusColor = 0xFFFF7744; return; }
        if (tab == 0) loginOffline(v); else loginWithToken(v);
    }

    private void loginOffline(String username) {
        if (username.length() < 2 || username.length() > 16) {
            status = "Username must be 2–16 characters."; statusColor = 0xFFFF7744; return;
        }
        String uuid = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString();
        if (applySession(new Session(username, uuid, "0", "legacy"))) {
            status = "Logged in as " + username; statusColor = 0xFF55EE55;
        }
    }

    private void loginWithToken(final String token) {
        loading = true; status = "Connecting..."; statusColor = 0xFFAAAAAA;
        new Thread(() -> {
            try {
                String json = httpGet(MC_PROFILE_URL, "Bearer " + token);
                String name = extractJson(json, "name"), id = extractJson(json, "id");
                if (name != null && id != null) {
                    if (applySession(new Session(name, dashUuid(id), token, "mojang")))
                        { status = "Logged in as " + name; statusColor = 0xFF55EE55; }
                } else { status = "Invalid token or no license."; statusColor = 0xFFFF4444; }
            } catch (Exception e) { status = "Error: " + e.getMessage(); statusColor = 0xFFFF4444; }
            loading = false;
        }, "AltManager-Token").start();
    }

    private void startMicrosoftLogin() {
        msCancelled = false; msState = 1; status = "";
        new Thread(() -> {
            try {

                String dcResp = httpPostRaw(MS_DEVICE_URL, "application/x-www-form-urlencoded",
                        "client_id=" + CLIENT_ID + "&scope=XboxLive.signin%20offline_access");

                String deviceCode = extractJson(dcResp, "device_code");
                String userCode   = extractJson(dcResp, "user_code");
                String verifyUri  = extractJson(dcResp, "verification_uri");
                String intervalS  = extractJson(dcResp, "interval");
                if (deviceCode == null || userCode == null) {
                    status = "Failed to get device code: " + dcResp; statusColor = 0xFFFF4444; msState = 0; return;
                }
                msDeviceCode = deviceCode; msUserCode = userCode;
                msVerifyUrl  = verifyUri != null ? verifyUri : "microsoft.com/devicelogin";
                codeShowTime = System.currentTimeMillis();
                copyText(msVerifyUrl);
                msState = 2;
                int interval = 5;
                try { interval = Integer.parseInt(intervalS); } catch (Exception ignored) {}

                String msToken = pollForToken(interval);
                if (msToken == null) return;
                msState = 3;

                String xblResp = httpPostRaw(XBL_URL, "application/json",
                        "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\","
                        + "\"RpsTicket\":\"d=" + msToken + "\"},"
                        + "\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}");
                String xblToken = extractJson(xblResp, "Token");
                String uhs      = extractJson(xblResp, "uhs");
                if (xblToken == null || uhs == null) {
                    status = "Xbox Live auth failed."; statusColor = 0xFFFF4444; msState = 0; return;
                }

                String xstsResp = httpPostRaw(XSTS_URL, "application/json",
                        "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xblToken + "\"]},"
                        + "\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}");
                String xstsToken = extractJson(xstsResp, "Token");
                if (xstsToken == null) {

                    String xe = extractJsonNum(xstsResp, "XErr");
                    status = "2148916233".equals(xe) ? "No Xbox account — create one at xbox.com."
                           : "2148916238".equals(xe) ? "Child account needs a family group."
                           : "XSTS failed" + (xe != null ? " (XErr " + xe + ")" : "") + ".";
                    statusColor = 0xFFFF4444; msState = 0; return;
                }

                String mcResp = httpPostRaw(MC_AUTH_URL, "application/json",
                        "{\"identityToken\":\"XBL3.0 x=" + uhs + ";" + xstsToken + "\"}");
                String mcToken = extractJson(mcResp, "access_token");
                if (mcToken == null) {

                    status = "Blocked by Mojang. Use the Token tab instead.";
                    statusColor = 0xFFFF7744; msState = 0; return;
                }

                String prof = httpGet(MC_PROFILE_URL, "Bearer " + mcToken);
                String name = extractJson(prof, "name");
                String id   = extractJson(prof, "id");
                if (name == null || id == null) {
                    status = "No Minecraft license on this account."; statusColor = 0xFFFF4444; msState = 0; return;
                }
                if (applySession(new Session(name, dashUuid(id), mcToken, "mojang"))) {
                    status = "Logged in as " + name; statusColor = 0xFF55EE55; msState = 4;
                } else { msState = 0; }

            } catch (Exception e) {
                status = "Error: " + e.getMessage(); statusColor = 0xFFFF4444; msState = 0;
            }
        }, "AltManager-MS").start();
    }

    private String pollForToken(int intervalSec) throws InterruptedException {
        long pollMs = Math.max(3000L, intervalSec * 1000L);
        String body = "client_id=" + CLIENT_ID
                + "&device_code=" + msDeviceCode
                + "&grant_type=urn:ietf:params:oauth:grant-type:device_code";
        while (!msCancelled) {
            Thread.sleep(pollMs);
            if (msCancelled) break;
            try {
                String resp  = httpPostRaw(MS_TOKEN_URL, "application/x-www-form-urlencoded", body);
                String error = extractJson(resp, "error");
                if (error == null) { String t = extractJson(resp, "access_token"); if (t != null) return t; }
                if ("authorization_pending".equals(error)) continue;
                else if ("slow_down".equals(error)) pollMs += 5000L;
                else if ("authorization_declined".equals(error)) {
                    status = "Sign-in declined."; statusColor = 0xFFFF4444; msState = 0; return null;
                } else if ("expired_token".equals(error)) {
                    status = "Code expired. Try again."; statusColor = 0xFFFF7744; msState = 0; return null;
                } else if (error != null) {
                    status = "Error: " + error; statusColor = 0xFFFF4444; msState = 0; return null;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void cancelMicrosoft() { msCancelled = true; }

    private boolean applySession(Session session) {
        try {
            for (Field f : net.minecraft.client.Minecraft.class.getDeclaredFields()) {
                if (f.getType() == Session.class) { f.setAccessible(true); f.set(mc, session); return true; }
            }
            status = "Session field not found."; statusColor = 0xFFFF4444;
        } catch (Exception e) { status = "Reflection error: " + e.getMessage(); statusColor = 0xFFFF4444; }
        return false;
    }

    private void importFromLauncher() {
        String appData = System.getenv("APPDATA");
        if (appData == null) { status = "APPDATA not found."; statusColor = 0xFFFF4444; return; }

        String[] paths = {
            appData + "\\.minecraft\\launcher_accounts.json",
            appData + "\\Minecraft Launcher\\Data\\accounts.json",
            appData + "\\Minecraft Launcher\\data\\accounts.json",
        };

        for (String path : paths) {
            java.io.File f = new java.io.File(path);
            if (!f.exists()) continue;
            try {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                String json = sb.toString();

                String token = extractJson(json, "access_token");
                if (token == null) token = extractJson(json, "accessToken");
                if (token != null && !token.isEmpty()) {
                    inputField.setText(token);
                    status = "Token imported — click Login.";
                    statusColor = 0xFF55AAFF;
                    return;
                }
            } catch (Exception ignored) {}
        }
        status = "Launcher accounts file not found.";
        statusColor = 0xFFFF7744;
    }

    private static void copyText(String s) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(s), null);
        } catch (Exception ignored) {}
    }

    private static String httpPostRaw(String u, String ct, String body) throws Exception {
        HttpURLConnection c = openPost(u, ct, body);
        int code = c.getResponseCode();
        return readStream(code >= 400 ? c.getErrorStream() : c.getInputStream());
    }

    private static String httpPost(String u, String ct, String body) throws Exception {
        HttpURLConnection c = openPost(u, ct, body);
        int code = c.getResponseCode();
        if (code >= 400) throw new IOException("HTTP " + code + ": " + readStream(c.getErrorStream()));
        return readStream(c.getInputStream());
    }

    private static HttpURLConnection openPost(String u, String ct, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", ct);
        c.setRequestProperty("Accept", "application/json");
        c.setDoOutput(true); c.setConnectTimeout(8000); c.setReadTimeout(8000);
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        c.setRequestProperty("Content-Length", String.valueOf(b.length));
        try (OutputStream os = c.getOutputStream()) { os.write(b); }
        return c;
    }

    private static String httpGet(String u, String auth) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestMethod("GET");
        c.setRequestProperty("Authorization", auth);
        c.setRequestProperty("Accept", "application/json");
        c.setConnectTimeout(8000); c.setReadTimeout(8000);
        return readStream(c.getInputStream());
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(); String l;
        while ((l = r.readLine()) != null) sb.append(l);
        r.close(); return sb.toString();
    }

    private static String extractJson(String json, String key) {
        if (json == null) return null;
        String s = "\"" + key + "\":\"";
        int i = json.indexOf(s); if (i == -1) return null;
        i += s.length(); int e = json.indexOf('"', i);
        return e == -1 ? null : json.substring(i, e);
    }

    private static String extractJsonNum(String json, String key) {
        if (json == null) return null;
        String s = "\"" + key + "\":";
        int i = json.indexOf(s); if (i == -1) return null;
        i += s.length();
        int e = i;
        while (e < json.length() && Character.isDigit(json.charAt(e))) e++;
        return e > i ? json.substring(i, e) : null;
    }

    private static String dashUuid(String r) {
        return r.replaceAll("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
    }

    private static boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static int blend(int a, int b, float t) {
        int ar=(a>>16)&0xFF,ag=(a>>8)&0xFF,ab=a&0xFF,aa=(a>>24)&0xFF;
        int br=(b>>16)&0xFF,bg=(b>>8)&0xFF,bb=b&0xFF,ba=(b>>24)&0xFF;
        return ((int)(aa+(ba-aa)*t)<<24)|((int)(ar+(br-ar)*t)<<16)
              |((int)(ag+(bg-ag)*t)<<8)|(int)(ab+(bb-ab)*t);
    }

    private void drawSemiBold(String s, float x, float y, int c) {
        if (FontUtil.semiBold != null) FontUtil.semiBold.drawSmoothString(s, x, y, c);
        else mc.fontRendererObj.drawString(s, (int)x, (int)y, c, false);
    }

    private void drawSmall(String s, float x, float y, int c) {
        if (FontUtil.small != null) FontUtil.small.drawSmoothString(s, x, y, c);
        else mc.fontRendererObj.drawString(s, (int)x, (int)y, c, false);
    }

    private int sw(String s) {
        return FontUtil.semiBold != null ? (int)FontUtil.semiBold.getStringWidth(s)
                : mc.fontRendererObj.getStringWidth(s);
    }

    private int smallW(String s) {
        return FontUtil.small != null ? (int)FontUtil.small.getStringWidth(s)
                : mc.fontRendererObj.getStringWidth(s);
    }

    private class CTextField {

        private final StringBuilder text = new StringBuilder();
        private final int maxLen;

        private int  cursor    = 0;
        private int  selStart  = 0;
        private int  selEnd    = 0;
        private boolean selecting = false;

        private boolean focused   = true;
        private long    blinkBase = System.currentTimeMillis();

        private float scrollPx = 0;

        CTextField(int maxLen) { this.maxLen = maxLen; }

        void clear()            { text.setLength(0); cursor = 0; selStart = selEnd = 0; scrollPx = 0; }
        void setFocused(boolean f) { focused = f; resetBlink(); }
        boolean isFocused()    { return focused; }
        String getText()       { return text.toString(); }
        void setText(String value) {
            text.setLength(0);
            if (value != null) {
                text.append(value, 0, Math.min(value.length(), maxLen));
            }
            cursor = text.length();
            selStart = selEnd = cursor;
            scrollPx = 0;
            resetBlink();
        }

        void tick() {  }

        void draw(int x, int y, int w, int h, int mx, int my) {

            float focusAlpha = focused ? 0.28F : 0.10F;
            int borderColor = focused
                    ? (int)(focusAlpha * 255) << 24 | 0xFFFFFF
                    : 0x1AFFFFFF;

            RenderUtils.drawRoundedRectAA(x, y, x + w, y + h, 8, 0xFF0F1318);
            RenderUtils.drawRoundedOutline(x, y, x + w, y + h, 8, 1.0F, borderColor);

            if (focused) {
                RenderUtils.drawFlowingGradientRect(x + 3, y + h - 3, x + w - 3, y + h - 1,
                        100, 0);
            }

            int padX = 8, padY = (h - charHeight()) / 2;
            int innerW = w - padX * 2;

            String full = text.toString();
            float cursorPx = measureWidth(full.substring(0, cursor));
            if (cursorPx - scrollPx > innerW - 2) scrollPx = cursorPx - innerW + 2;
            if (cursorPx - scrollPx < 0)          scrollPx = cursorPx;
            if (scrollPx < 0) scrollPx = 0;

            net.minecraft.client.gui.ScaledResolution sr =
                    new net.minecraft.client.gui.ScaledResolution(mc);
            int sf = sr.getScaleFactor();
            int clipX = x + padX, clipY = y + 2, clipW = innerW, clipH = h - 4;
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            org.lwjgl.opengl.GL11.glScissor(
                    clipX * sf,
                    (mc.displayHeight - (clipY + clipH) * sf),
                    clipW * sf, clipH * sf);

            float textX = x + padX - scrollPx;
            float textY = y + padY;

            if (hasSelection()) {
                int s0 = Math.min(selStart, selEnd), s1 = Math.max(selStart, selEnd);
                float sx0 = textX + measureWidth(full.substring(0, s0));
                float sx1 = textX + measureWidth(full.substring(0, s1));
                Gui.drawRect((int)sx0, y + 2, (int)sx1, y + h - 2, 0x553399FF);
            }

            if (full.isEmpty() && !focused) {

            } else {
                drawFieldText(full, textX, textY, 0xFFE0E6F0);
            }

            if (focused) {
                long elapsed = System.currentTimeMillis() - blinkBase;
                if ((elapsed / 530) % 2 == 0) {
                    float cx2 = textX + measureWidth(full.substring(0, cursor));
                    GlStateManager.color(1F, 1F, 1F, 0.85F);
                    Gui.drawRect((int)cx2, y + 4, (int)cx2 + 1, y + h - 4, 0xFFCCDDFF);
                    GlStateManager.color(1F, 1F, 1F, 1F);
                }
            }

            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        }

        void mouseClicked(int mx, int my, int fx, int fy, int fw, int fh) {
            boolean wasInside = over(mx, my, fx, fy, fw, fh);
            focused = wasInside;
            if (wasInside) {
                resetBlink();
                int padX = 8;
                float relX = mx - (fx + padX) + scrollPx;
                cursor = charAtPixel(relX);
                selStart = selEnd = cursor;
            }
        }

        void keyTyped(char typedChar, int keyCode) {
            if (!focused) return;
            boolean ctrl  = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
            boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)   || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

            if (ctrl && keyCode == Keyboard.KEY_A) {
                selStart = 0; selEnd = text.length(); cursor = text.length(); resetBlink(); return;
            }
            if (ctrl && keyCode == Keyboard.KEY_C) {
                if (hasSelection()) copyToClipboard(selectedText()); return;
            }
            if (ctrl && keyCode == Keyboard.KEY_X) {
                if (hasSelection()) { copyToClipboard(selectedText()); deleteSelection(); } return;
            }
            if (ctrl && keyCode == Keyboard.KEY_V) {
                deleteSelection(); String clip = pasteFromClipboard();
                if (!clip.isEmpty()) insertText(clip); return;
            }

            if (keyCode == Keyboard.KEY_LEFT) {
                if (cursor > 0) {
                    if (shift) { if (!hasSelection()) selStart = cursor; cursor--; selEnd = cursor; }
                    else { cursor = hasSelection() ? Math.min(selStart, selEnd) : cursor - 1; clearSel(); }
                } else if (!shift) clearSel();
                resetBlink(); return;
            }
            if (keyCode == Keyboard.KEY_RIGHT) {
                if (cursor < text.length()) {
                    if (shift) { if (!hasSelection()) selStart = cursor; cursor++; selEnd = cursor; }
                    else { cursor = hasSelection() ? Math.max(selStart, selEnd) : cursor + 1; clearSel(); }
                } else if (!shift) clearSel();
                resetBlink(); return;
            }
            if (keyCode == Keyboard.KEY_HOME) {
                if (shift) { if (!hasSelection()) selStart = cursor; cursor = 0; selEnd = 0; }
                else { cursor = 0; clearSel(); }
                resetBlink(); return;
            }
            if (keyCode == Keyboard.KEY_END) {
                if (shift) { if (!hasSelection()) selStart = cursor; cursor = text.length(); selEnd = cursor; }
                else { cursor = text.length(); clearSel(); }
                resetBlink(); return;
            }
            if (keyCode == Keyboard.KEY_BACK) {
                if (hasSelection()) deleteSelection();
                else if (cursor > 0) { text.deleteCharAt(cursor - 1); cursor--; }
                resetBlink(); return;
            }
            if (keyCode == Keyboard.KEY_DELETE) {
                if (hasSelection()) deleteSelection();
                else if (cursor < text.length()) text.deleteCharAt(cursor);
                resetBlink(); return;
            }

            if (typedChar >= 32 && typedChar != 127) {
                deleteSelection();
                if (text.length() < maxLen) { text.insert(cursor, typedChar); cursor++; }
                resetBlink();
            }
        }

        private boolean hasSelection() { return selStart != selEnd; }
        private void clearSel()        { selStart = selEnd = cursor; }
        private void resetBlink()      { blinkBase = System.currentTimeMillis(); }

        private String selectedText() {
            int s = Math.min(selStart, selEnd), e = Math.max(selStart, selEnd);
            return text.substring(s, e);
        }

        private void deleteSelection() {
            if (!hasSelection()) return;
            int s = Math.min(selStart, selEnd), e = Math.max(selStart, selEnd);
            text.delete(s, e);
            cursor = s; selStart = selEnd = s;
        }

        private void insertText(String s) {
            for (char c : s.toCharArray()) {
                if (text.length() < maxLen && (c >= 32 && c != 127)) {
                    text.insert(cursor, c); cursor++;
                }
            }
        }

        private int charAtPixel(float px) {
            String full = text.toString();
            for (int i = 0; i <= full.length(); i++) {
                float mid = measureWidth(full.substring(0, i))
                        + (i < full.length() ? measureWidth(full.substring(i, i + 1)) / 2F : 0F);
                if (px <= mid) return i;
            }
            return full.length();
        }

        private float measureWidth(String s) {
            if (FontUtil.small != null) return (float) FontUtil.small.getStringWidth(s);
            return mc.fontRendererObj.getStringWidth(s);
        }

        private int charHeight() {
            if (FontUtil.small != null) return (int)FontUtil.small.getStringWidth("I") + 4;
            return mc.fontRendererObj.FONT_HEIGHT;
        }

        private void drawFieldText(String s, float x, float y, int color) {
            if (FontUtil.small != null) FontUtil.small.drawSmoothString(s, x, y, color);
            else mc.fontRendererObj.drawString(s, (int)x, (int)y, color, false);
        }

        private void copyToClipboard(String s) {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(s), null);
            } catch (Exception ignored) {}
        }

        private String pasteFromClipboard() {
            try {
                Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
                if (t != null && t.isDataFlavorSupported(DataFlavor.stringFlavor))
                    return (String) t.getTransferData(DataFlavor.stringFlavor);
            } catch (Exception ignored) {}
            return "";
        }
    }
}
