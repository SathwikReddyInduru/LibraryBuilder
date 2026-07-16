package com.xius.TariffBuilder.exception;

public class TariffInsertException extends RuntimeException {

    private final String step;
    private final String failedTable;

    public TariffInsertException(String step, String failedTable, Throwable cause) {
        super("Error in " + step + " at " + failedTable, cause);
        this.step = step;
        this.failedTable = failedTable;
    }

    public String getStep() {
        return step;
    }

    public String getFailedTable() {
        return failedTable;
    }

    public java.sql.SQLException getSqlCause() {
        Throwable t = getCause();
        while (t != null) {
            if (t instanceof java.sql.SQLException) {
                return (java.sql.SQLException) t;
            }
            t = t.getCause();
        }
        return null;
    }
}
