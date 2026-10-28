package br.app.meucorre;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Arrays;

/** Ponte entre o app (JavaScript) e o leitor de ofertas. */
@CapacitorPlugin(name = "Oferta")
public class OfertaPlugin extends Plugin {

    @PluginMethod
    public void setConfig(PluginCall call) {
        Double custoKm = call.getDouble("custoKm", 0.38);
        Double minKm = call.getDouble("minKm", 1.8);
        Boolean ativo = call.getBoolean("ativo", true);
        OfertaService.prefs(getContext()).edit()
                .putFloat("custoKm", custoKm == null ? 0.38f : custoKm.floatValue())
                .putFloat("minKm", minKm == null ? 1.8f : minKm.floatValue())
                .putBoolean("ativo", ativo == null || ativo)
                .apply();
        call.resolve();
    }

    @PluginMethod
    public void status(PluginCall call) {
        SharedPreferences p = OfertaService.prefs(getContext());
        JSObject r = new JSObject();
        r.put("ligado", servicoLigado());
        r.put("ultima", p.getString("ultima", ""));
        r.put("ultimaEm", p.getLong("ultimaEm", 0));
        r.put("falha", p.getString("falha", ""));
        r.put("falhaEm", p.getLong("falhaEm", 0));
        r.put("eventos", p.getInt("eventos", 0));
        r.put("eventoEm", p.getLong("eventoEm", 0));
        r.put("eventoTipo", p.getString("eventoTipo", ""));
        r.put("visto", p.getString("visto", ""));
        r.put("vistoEm", p.getLong("vistoEm", 0));
        r.put("erro", p.getString("erro", ""));
        r.put("rodando", OfertaService.instance != null);
        r.put("conectadoEm", p.getLong("conectadoEm", 0));
        r.put("gravarAte", p.getLong("gravarAte", 0));
        java.io.File f = new java.io.File(getContext().getFilesDir(), OfertaService.ARQ);
        r.put("gravado", f.exists() ? f.length() : 0);
        call.resolve(r);
    }

    @PluginMethod
    public void abrirConfiguracoes(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void abrirInfoApp(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(android.net.Uri.parse("package:" + getContext().getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void abrirLink(PluginCall call) {
        String url = call.getString("url", "");
        Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void gravar(PluginCall call) {
        int minutos = call.getInt("minutos", 15);
        java.io.File f = new java.io.File(getContext().getFilesDir(), OfertaService.ARQ);
        if (minutos > 0) f.delete();
        OfertaService.prefs(getContext()).edit()
                .putLong("gravarAte", minutos > 0 ? System.currentTimeMillis() + minutos * 60_000L : 0).apply();
        call.resolve();
    }

    @PluginMethod
    public void enviarDiagnostico(PluginCall call) {
        SharedPreferences p = OfertaService.prefs(getContext());
        StringBuilder sb = new StringBuilder();
        sb.append("DIAGNÓSTICO MEU CORRE\n");
        sb.append("Celular: ").append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL)
          .append(" · Android ").append(android.os.Build.VERSION.RELEASE).append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
        try {
            sb.append("Meu Corre: ").append(getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0).versionName).append('\n');
        } catch (Exception ignored) {}
        sb.append("Leitura ativada: ").append(servicoLigado()).append(" · rodando: ").append(OfertaService.instance != null).append('\n');
        sb.append("Sinais da 99: ").append(p.getInt("eventos", 0)).append(" · último tipo: ").append(p.getString("eventoTipo", "")).append('\n');
        sb.append("Última oferta lida: ").append(p.getString("ultima", "-")).append('\n');
        sb.append("Último texto com R$: ").append(p.getString("visto", "-")).append('\n');
        sb.append("Falha: ").append(p.getString("falha", "-")).append('\n');
        sb.append("Erro: ").append(p.getString("erro", "-")).append("\n\n");
        String grav = "";
        try {
            java.io.File f = new java.io.File(getContext().getFilesDir(), OfertaService.ARQ);
            if (f.exists()) {
                byte[] b = new byte[(int) f.length()];
                try (java.io.FileInputStream in = new java.io.FileInputStream(f)) { int o = 0, n; while (o < b.length && (n = in.read(b, o, b.length - o)) > 0) o += n; }
                grav = new String(b, "UTF-8");
            }
        } catch (Exception e) { grav = "erro lendo gravação: " + e; }
        if (grav.isEmpty()) grav = "(nada gravado: a leitura não recebeu nada da 99 durante a gravação)";
        // Prioriza os trechos com valores (R$) e o final da gravação; o WhatsApp aceita ~60 mil caracteres
        String[] blocos = grav.split("(?==== )");
        StringBuilder imp = new StringBuilder(), resto = new StringBuilder();
        for (String bl : blocos) {
            if (bl.contains("R$") || bl.toLowerCase().contains("aceitar")) imp.append(bl); else resto.append(bl);
        }
        String a = imp.length() > 40000 ? imp.substring(imp.length() - 40000) : imp.toString();
        int sobra = 58000 - a.length();
        String c = resto.length() > sobra ? resto.substring(resto.length() - sobra) : resto.toString();
        sb.append("--- telas com valores (").append(imp.length()).append(" caracteres) ---\n").append(a);
        sb.append("\n--- outras telas da 99 (").append(resto.length()).append(" caracteres) ---\n").append(c);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico Meu Corre");
        i.putExtra(Intent.EXTRA_TEXT, sb.toString());
        Intent ch = Intent.createChooser(i, "Enviar diagnóstico");
        ch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(ch);
        call.resolve();
    }

    @PluginMethod
    public void testar(PluginCall call) {
        JSObject r = new JSObject();
        OfertaService s = OfertaService.instance;
        if (s == null) {
            r.put("ok", false);
            call.resolve(r);
            return;
        }
        boolean ok = s.analisar(Arrays.asList("Entrega Food", "R$14,67", "Inclui bônus de R$5,00",
                "Distância total 5,7 km", "Aceitar pedido"), true);
        r.put("ok", ok);
        call.resolve(r);
    }

    private boolean servicoLigado() {
        String s = Settings.Secure.getString(getContext().getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (s == null) return false;
        for (String item : s.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(item);
            if (cn != null && cn.getPackageName().equals(getContext().getPackageName())
                    && cn.getClassName().equals(OfertaService.class.getName())) return true;
        }
        return false;
    }
}
