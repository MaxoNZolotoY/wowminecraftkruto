package com.wowcraft.core.util;

/** A localized string with English and Russian variants. */
public record L10n(String en, String ru) {
    public static L10n of(String en, String ru) {
        return new L10n(en, ru == null || ru.isEmpty() ? en : ru);
    }

    public static L10n of(String both) {
        return new L10n(both, both);
    }

    public String get(Lang lang) {
        return lang == Lang.RU ? ru : en;
    }

    public String get(String mcLanguageCode) {
        return get(Lang.fromCode(mcLanguageCode));
    }

    public enum Lang {
        EN, RU;

        public static Lang fromCode(String code) {
            return code != null && code.toLowerCase(java.util.Locale.ROOT).startsWith("ru") ? RU : EN;
        }
    }
}
