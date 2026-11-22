package com.cnhsk.auth.service;

import org.springframework.web.util.HtmlUtils;

/**
 * One account email, rendered as plain text and as HTML from the same words. The HTML uses tables and inline
 * styles because mail clients drop style sheets, and takes its logo from an attached image because a local
 * address cannot be reached from a mailbox.
 */
record AuthEmail(String subject, String heading, String intro, String code, String actionLabel,
        String actionUrl, String note) {

    static final String LOGO_CONTENT_ID = "hoc-ba-logo";

    private static final String FONT = "font-family:'Be Vietnam Pro',Arial,Helvetica,sans-serif;";

    static AuthEmail withCode(String subject, String heading, String intro, String code, String note) {
        return new AuthEmail(subject, heading, intro, code, null, null, note);
    }

    static AuthEmail withLink(String subject, String heading, String intro, String actionLabel,
            String actionUrl, String note) {
        return new AuthEmail(subject, heading, intro, null, actionLabel, actionUrl, note);
    }

    String text() {
        return "Xin chào,\n\n"
                + intro + "\n\n"
                + (code == null ? actionUrl : code) + "\n\n"
                + note + "\n\n"
                + "CNHSK";
    }

    String html() {
        return "<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<meta name=\"color-scheme\" content=\"light\"></head>"
                + "<body style=\"margin:0;padding:0;background:#faf7f2;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background:#faf7f2;\"><tr><td align=\"center\" style=\"padding:32px 16px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:520px;background:#ffffff;border-radius:12px;\">"
                + "<tr><td style=\"padding:28px 32px 0;\"><img src=\"cid:" + LOGO_CONTENT_ID + "\" width=\"84\" "
                + "height=\"56\" alt=\"Học Bá\" style=\"display:block;border:0;\"></td></tr>"
                + "<tr><td style=\"padding:20px 32px 32px;" + FONT + "color:#2f3033;font-size:16px;"
                + "line-height:24px;\">"
                + "<h1 style=\"margin:0 0 12px;font-size:22px;line-height:30px;font-weight:700;\">"
                + escape(heading) + "</h1>"
                + "<p style=\"margin:0 0 20px;\">" + escape(intro) + "</p>"
                + (code == null ? link() : codeBlock())
                + "<p style=\"margin:0;font-size:14px;line-height:21px;color:#62646a;\">" + escape(note) + "</p>"
                + "</td></tr></table>"
                + "<p style=\"margin:16px 0 0;" + FONT + "font-size:12px;line-height:18px;color:#62646a;\">"
                + "CNHSK · Học tiếng Trung cùng Học Bá</p>"
                + "</td></tr></table></body></html>";
    }

    private String codeBlock() {
        return "<p style=\"margin:0 0 20px;font-family:Consolas,Menlo,monospace;font-size:34px;line-height:44px;"
                + "font-weight:700;letter-spacing:8px;color:#af0000;\">" + escape(code) + "</p>";
    }

    private String link() {
        String url = escape(actionUrl);
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 20px;\"><tr>"
                + "<td style=\"background:#af0000;border-radius:8px;\"><a href=\"" + url + "\" "
                + "style=\"display:inline-block;padding:12px 24px;" + FONT + "font-size:16px;line-height:20px;"
                + "font-weight:600;color:#ffffff;text-decoration:none;\">" + escape(actionLabel) + "</a></td>"
                + "</tr></table>"
                + "<p style=\"margin:0 0 20px;font-size:14px;line-height:21px;color:#62646a;word-break:break-all;\">"
                + "Nút không mở được? Dán liên kết này vào trình duyệt:<br><a href=\"" + url + "\" "
                + "style=\"color:#8f0000;\">" + url + "</a></p>";
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
