package com.meicrypt.identity.notification.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal Handlebars-style renderer for notification templates (Phase 10).
 *
 * Supports {@code {{key}}} placeholder substitution with HTML-safe defaults
 * (values are inserted verbatim; treat outputs as untrusted plain text).
 */
@Component
public class NotificationRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*}}");

    public String render(String template, Map<String, String> parameters) {
        if (template == null || template.isBlank()) {
            return template;
        }
        Map<String, String> params = parameters == null ? Map.of() : parameters;
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder(template.length());
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = params.getOrDefault(key, "");
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
