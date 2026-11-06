package br.app.sobrareal;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Funções do Android usadas pelo app: atualização, bateria, relatório em PDF e backup. */
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

    /** Mantém a página do relatório viva enquanto o Android gera o PDF. */
    private WebView paginaImpressao;

    /** Abre a tela de impressão do Android com o relatório; nela o usuário escolhe "Salvar como PDF". */
    @PluginMethod
    public void imprimir(PluginCall call) {
        final String html = call.getString("html", "");
        final String nome = call.getString("nome", "Relatorio");
        getActivity().runOnUiThread(() -> {
            try {
                WebView wv = new WebView(getActivity());
                wv.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        PrintManager pm = (PrintManager) getActivity().getSystemService(Context.PRINT_SERVICE);
                        PrintDocumentAdapter adapter = view.createPrintDocumentAdapter(nome);
                        pm.print(nome, adapter, new PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
                    }
                });
                paginaImpressao = wv;
                wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
                call.resolve();
            } catch (Exception e) {
                call.reject("Não foi possível abrir a impressão: " + e.getMessage());
            }
        });
    }

    /** Salva um arquivo de texto (backup) na pasta Downloads do celular. */
    @PluginMethod
    public void salvarDownload(PluginCall call) {
        String nome = call.getString("nome", "arquivo.txt");
        String conteudo = call.getString("conteudo", "");
        String mime = call.getString("mime", "text/plain");
        byte[] bytes = conteudo.getBytes(StandardCharsets.UTF_8);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, nome);
                v.put(MediaStore.Downloads.MIME_TYPE, mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContext().getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new Exception("sem acesso à pasta Downloads");
                try (OutputStream o = getContext().getContentResolver().openOutputStream(uri)) { o.write(bytes); }
            } else {
                File dir = getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir != null && !dir.exists()) dir.mkdirs();
                try (FileOutputStream o = new FileOutputStream(new File(dir, nome))) { o.write(bytes); }
            }
            call.resolve();
        } catch (Exception e) {
            call.reject("Não foi possível salvar: " + e.getMessage());
        }
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
