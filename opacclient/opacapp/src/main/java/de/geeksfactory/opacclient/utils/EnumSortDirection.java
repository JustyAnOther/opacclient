package de.geeksfactory.opacclient.utils;

import de.geeksfactory.opacclient.R;

public enum EnumSortDirection {
    DESC("DESC", R.string.sort_direction_desc), ASC("ASC", R.string.sort_direction_asc);

    final String sqlText;
    final int textId;

    EnumSortDirection(String sqlText, int textId) {
        this.sqlText = sqlText;
        this.textId = textId;
    }

    public EnumSortDirection swap() {
        if (this == ASC) {
            return DESC;
        } else {
            return ASC;
        }
    }

    public int getTextId() {
        return textId;
    }
    public String getSqlText() { return sqlText; }
}
