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
