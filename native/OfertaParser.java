package br.app.meucorre;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lê os textos da tela de oferta da 99 e extrai valor, bônus e distância. Sem dependências do Android (testável). */
public final class OfertaParser {
    public static final class Oferta {
        public final double valor, bonus, km;
        Oferta(double valor, double bonus, double km) { this.valor = valor; this.bonus = bonus; this.km = km; }
    }

    private static final Pattern VALOR = Pattern.compile("R\\$\\s*([0-9]{1,3}(?:\\.[0-9]{3})*,[0-9]{2}|[0-9]+,[0-9]{2})");
    private static final Pattern KM = Pattern.compile("([0-9]+(?:[,.][0-9]+)?)\\s*km\\b", Pattern.CASE_INSENSITIVE);

    private OfertaParser() {}

    static double num(String s) {
        s = s.trim();
        if (s.contains(",")) s = s.replace(".", "").replace(",", ".");
        return Double.parseDouble(s);
    }

    private static boolean temBonus(String s) {
        String l = s.toLowerCase(Locale.forLanguageTag("pt-BR"));
        return l.contains("bônus") || l.contains("bonus");
    }

    /** Retorna null quando a tela não é uma oferta ou quando faltam números. */
    public static Oferta extrair(List<String> textos) {
        boolean ehOferta = false;
        for (String s : textos) {
            String l = s.toLowerCase(Locale.forLanguageTag("pt-BR"));
            if (l.contains("aceitar")) { ehOferta = true; break; }
        }
        if (!ehOferta) return null;

        Double valor = null, bonus = null, km = null;
        for (int i = 0; i < textos.size(); i++) {
            String s = textos.get(i);
            Matcher m = VALOR.matcher(s);
            if (!m.find()) continue;
            double v = num(m.group(1));
            boolean ehBonus = temBonus(s) || (i > 0 && temBonus(textos.get(i - 1)) && !VALOR.matcher(textos.get(i - 1)).find());
            if (ehBonus) { if (bonus == null) bonus = v; }
            else if (valor == null) valor = v;
        }

        for (int i = 0; i < textos.size() && km == null; i++) {
            String l = textos.get(i).toLowerCase(Locale.forLanguageTag("pt-BR"));
            if (l.contains("distância total") || l.contains("distancia total")) {
                Matcher m = KM.matcher(textos.get(i));
                if (m.find()) km = num(m.group(1));
                else if (i + 1 < textos.size()) {
                    Matcher m2 = KM.matcher(textos.get(i + 1));
                    if (m2.find()) km = num(m2.group(1));
                }
            }
        }
        if (km == null) { // sem "distância total": soma os trechos em km (coleta + entrega)
            double soma = 0; boolean achou = false;
            for (String s : textos) {
                Matcher m = KM.matcher(s);
                while (m.find()) { soma += num(m.group(1)); achou = true; }
            }
            if (achou) km = soma;
        }
        if (valor == null || km == null || km <= 0) return null;
        return new Oferta(valor, bonus == null ? 0 : bonus, km);
    }
}
