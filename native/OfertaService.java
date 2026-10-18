package br.app.meucorre;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lê a tela de oferta do app 99 (com.app99.driver) e mostra um balão com o valor por km e o que sobra. */
public class OfertaService extends AccessibilityService {
    static volatile OfertaService instance;
    static final String PREFS = "meucorre_oferta";
    private static final Locale BR = Locale.forLanguageTag("pt-BR");

    private WindowManager wm;
    private TextView balao;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable esconder = this::esconder;
    private String ultimaAssinatura = "";

    @Override protected void onServiceConnected() {
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override public boolean onUnbind(Intent intent) {
        instance = null;
        esconder();
        return super.onUnbind(intent);
    }

    @Override public void onInterrupt() {}

    static final String PKG = "com.app99.driver";
    private long ultimoDiag = 0;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!prefs(this).getBoolean("ativo", true)) return;
        try {
            List<List<String>> telas = new ArrayList<>();
            // 1) Notificação da 99 (oferta chegando com o app em segundo plano)
            if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
                List<String> t = new ArrayList<>();
                for (CharSequence c : event.getText()) if (c != null) t.add(c.toString());
                android.os.Parcelable pd = event.getParcelableData();
                if (pd instanceof android.app.Notification) {
                    android.os.Bundle ex = ((android.app.Notification) pd).extras;
                    for (String k : new String[]{"android.title", "android.text", "android.bigText", "android.subText"}) {
                        CharSequence c = ex.getCharSequence(k);
                        if (c != null) t.add(c.toString());
                    }
                }
                telas.add(t);
            }
            // 2) Todas as janelas da 99 na tela (inclui o balão de oferta sobre outros apps)
            try {
                for (android.view.accessibility.AccessibilityWindowInfo w : getWindows()) {
                    AccessibilityNodeInfo r = w.getRoot();
                    if (r == null || r.getPackageName() == null || !PKG.contentEquals(r.getPackageName())) continue;
                    List<String> t = new ArrayList<>();
                    coletar(r, t, 0);
                    telas.add(t);
                }
            } catch (Exception ignored) {}
            // 3) A janela de onde veio o evento
            AccessibilityNodeInfo src = event.getSource();
            if (src != null) {
                AccessibilityNodeInfo r = src;
                for (int i = 0; i < 60; i++) { AccessibilityNodeInfo pa = r.getParent(); if (pa == null) break; r = pa; }
                List<String> t = new ArrayList<>();
                coletar(r, t, 0);
                telas.add(t);
            }
            // 4) Janela ativa (como antes)
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null && PKG.contentEquals(root.getPackageName())) {
                List<String> t = new ArrayList<>();
                coletar(root, t, 0);
                telas.add(t);
            }
            diagnostico(event, telas);
            List<String> alvo = null;
            for (List<String> t : telas) if (OfertaParser.extrair(t) != null) { alvo = t; break; }
            if (alvo == null) for (List<String> t : telas) {
                for (String x : t) if (x.toLowerCase(BR).contains("aceitar")) { alvo = t; break; }
                if (alvo != null) break;
            }
            analisar(alvo == null ? new ArrayList<>() : alvo, false);
        } catch (Exception e) {
            prefs(this).edit().putString("erro", e.toString()).apply();
        }
    }

    /** Guarda o que a leitura está vendo, para o card "Ofertas da 99" mostrar. */
    private void diagnostico(AccessibilityEvent event, List<List<String>> telas) {
        long agora = System.currentTimeMillis();
        SharedPreferences p = prefs(this);
        SharedPreferences.Editor e = p.edit();
        boolean temValor = false;
        StringBuilder sb = new StringBuilder();
        for (List<String> t : telas) for (String s : t) {
            if (s.contains("R$")) temValor = true;
            if (sb.length() < 700) sb.append(s.replace('\n', ' ')).append(" | ");
        }
        if (temValor) e.putString("visto", sb.toString()).putLong("vistoEm", agora);
        if (agora - ultimoDiag > 3000 || temValor) {
            ultimoDiag = agora;
            e.putInt("eventos", p.getInt("eventos", 0) + 1).putLong("eventoEm", agora)
             .putString("eventoTipo", AccessibilityEvent.eventTypeToString(event.getEventType()) + " · " + telas.size() + " janela(s)");
        }
        e.apply();
    }

    private static void coletar(AccessibilityNodeInfo n, List<String> out, int prof) {
        if (n == null || prof > 40) return;
        CharSequence t = n.getText();
        if (t != null && t.length() > 0) out.add(t.toString());
        CharSequence d = n.getContentDescription();
        if (d != null && d.length() > 0) out.add(d.toString());
        for (int i = 0; i < n.getChildCount(); i++) coletar(n.getChild(i), out, prof + 1);
    }

    /** Analisa os textos da tela. Também usado pelo botão "Testar balão" do app. */
    boolean analisar(List<String> textos, boolean teste) {
        OfertaParser.Oferta o = OfertaParser.extrair(textos);
        SharedPreferences p = prefs(this);
        if (o == null) {
            // Era uma oferta, mas não deu para ler os números: guarda os textos para diagnóstico
            for (String s : textos) {
                if (s.toLowerCase(BR).contains("aceitar")) {
                    p.edit().putString("falha", String.join(" | ", textos)).putLong("falhaEm", System.currentTimeMillis()).apply();
                    break;
                }
            }
            if (balao != null && !teste) { h.removeCallbacks(esconder); h.postDelayed(esconder, 1500); }
            return false;
        }
        String assinatura = o.valor + "|" + o.km;
        if (assinatura.equals(ultimaAssinatura) && balao != null && !teste) return true;
        ultimaAssinatura = assinatura;

        double custoKm = p.getFloat("custoKm", 0.38f);
        double minKm = p.getFloat("minKm", 1.8f);
        double porKm = o.valor / o.km;
        double sobra = o.valor - o.km * custoKm;
        int cor;
        String selo;
        if (porKm >= minKm) { cor = Color.parseColor("#1E7F4F"); selo = "VALE A PENA"; }
        else if (porKm >= minKm * 0.85) { cor = Color.parseColor("#B45309"); selo = "NO LIMITE"; }
        else { cor = Color.parseColor("#B42318"); selo = "ABAIXO DA META"; }

        String l1 = String.format(BR, "R$ %.2f/km · %s", porKm, selo);
        String l2 = String.format(BR, "Sobra R$ %.2f em %.1f km", sobra, o.km);
        if (o.bonus > 0) l2 += String.format(BR, "\nSem o bônus: R$ %.2f/km", (o.valor - o.bonus) / o.km);
        mostrar(l1 + "\n" + l2, cor);

        p.edit().putString("ultima", String.format(BR, "R$ %.2f · %.1f km · R$ %.2f/km", o.valor, o.km, porKm))
                .putLong("ultimaEm", System.currentTimeMillis()).apply();
        return true;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void mostrar(String texto, int cor) {
        h.post(() -> {
            if (wm == null) return;
            if (balao == null) {
                TextView tv = new TextView(this);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                tv.setTypeface(Typeface.DEFAULT_BOLD);
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(dp(16), dp(10), dp(16), dp(10));
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                lp.y = dp(64);
                try { wm.addView(tv, lp); } catch (Exception e) { return; }
                balao = tv;
            }
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(cor);
            bg.setCornerRadius(dp(18));
            balao.setBackground(bg);
            balao.setText(texto);
            h.removeCallbacks(esconder);
            h.postDelayed(esconder, 25000);
        });
    }

    private void esconder() {
        h.post(() -> {
            if (balao != null && wm != null) {
                try { wm.removeView(balao); } catch (Exception ignored) {}
            }
            balao = null;
            ultimaAssinatura = "";
        });
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
