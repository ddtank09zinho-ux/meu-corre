package br.app.sobrareal;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Funções do Android usadas pelo app: link de atualização e situação da bateria. */
@CapacitorPlugin(name = "Util")
public class UtilPlugin extends Plugin {
    @PluginMethod
    public void abrirLink(PluginCall call) {
        String url = call.getString("url", "");
        Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    /** economia = modo economia de bateria ligado; otimizado = Android pode pausar o app em segundo plano. */
    @PluginMethod
    public void bateria(PluginCall call) {
        PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        JSObject r = new JSObject();
        r.put("economia", pm != null && pm.isPowerSaveMode());
        r.put("otimizado", pm != null && !pm.isIgnoringBatteryOptimizations(getContext().getPackageName()));
        r.put("fabricante", Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase());
        call.resolve(r);
    }

    @PluginMethod
    public void abrirEconomia(PluginCall call) {
        abrir(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), new Intent(Settings.ACTION_SETTINGS));
        call.resolve();
    }

    /** Tela de informações do app, onde fica Bateria → Sem restrições. */
    @PluginMethod
    public void abrirOtimizacao(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:" + getContext().getPackageName()));
        abrir(i, new Intent(Settings.ACTION_SETTINGS));
        call.resolve();
    }

    private void abrir(Intent principal, Intent reserva) {
        try {
            principal.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(principal);
        } catch (Exception e) {
            reserva.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(reserva);
        }
    }
}
