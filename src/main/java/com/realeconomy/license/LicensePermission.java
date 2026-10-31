package com.realeconomy.license;

/**
 * Permisos que puede otorgar una licencia personalizada (parte 5, 3ª tanda). El gobierno
 * crea licencias eligiendo uno de estos permisos; sin la licencia correcta, la acción
 * correspondiente queda bloqueada para los miembros del país.
 */
public enum LicensePermission {
    TIENDA("tienda", "montar una tienda"),
    SUBASTA("subasta", "colocar subastas"),
    BANQUERO("banquero", "actuar de banquero / prestar"),
    MERCADO("mercado", "montar un mercado"),
    TRABAJO("trabajo", "trabajar");

    private final String key;
    private final String description;

    LicensePermission(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public String description() {
        return description;
    }

    /** Resuelve un permiso por su clave (es/insensible a mayúsculas). Null si no existe. */
    public static LicensePermission byKey(String key) {
        if (key == null) {
            return null;
        }
        for (LicensePermission p : values()) {
            if (p.key.equalsIgnoreCase(key.trim())) {
                return p;
            }
        }
        return null;
    }
}
