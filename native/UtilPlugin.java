package br.app.meucorre;

import android.content.Intent;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Funções do Android usadas pelo app (abrir o link de atualização). */
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
}
