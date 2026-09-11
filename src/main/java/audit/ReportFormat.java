package audit;

/** How an {@link AuditReport} is written out: one renderer per format. */
public enum ReportFormat {

    TEXT {
        @Override
        public String render(AuditReport report) {
            return TextReport.render(report);
        }
    },
    JSON {
        @Override
        public String render(AuditReport report) {
            return JsonReport.render(report);
        }
    },
    SARIF {
        @Override
        public String render(AuditReport report) {
            return SarifReport.render(report);
        }
    };

    public abstract String render(AuditReport report);
}
