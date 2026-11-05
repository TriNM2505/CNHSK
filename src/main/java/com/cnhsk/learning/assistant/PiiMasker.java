package com.cnhsk.learning.assistant;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class PiiMasker {
    private static final String ATOM_CHARS = "a-z0-9!#$%&'*+/=?^_`{|}~-";
    private static final String ATOM = "[" + ATOM_CHARS + "]+";
    private static final String DOMAIN_LABEL = "[a-z0-9](?:[a-z0-9-]*[a-z0-9])?";
    private static final Pattern EMAIL = Pattern.compile(
            "(?<![" + ATOM_CHARS + "])" + ATOM + "(?:\\." + ATOM + ")*@"
                    + DOMAIN_LABEL + "(?:\\." + DOMAIN_LABEL + ")*\\.[a-z]{2,}"
                    + "(?![\\p{L}\\p{M}\\p{N}_@-])",
            Pattern.CASE_INSENSITIVE);
    // Consume the whole numeric run before checking its length and prefix.
    private static final Pattern NUMBER_RUN = Pattern.compile("\\+?[0-9](?:[ .-]?[0-9])*+");
    private static final Pattern PHONE = Pattern.compile("(?:0|\\+84)(?:[ .-]?[0-9]){9,10}");
    private static final Pattern ATTACHED = Pattern.compile("[\\p{L}\\p{M}\\p{N}_+]");

    public String mask(String text) {
        String withoutEmails = EMAIL.matcher(text).replaceAll("[email]");
        var numbers = NUMBER_RUN.matcher(withoutEmails);
        var result = new StringBuilder();
        while (numbers.find()) {
            boolean attachedBefore = numbers.start() > 0
                    && isAttached(withoutEmails.codePointBefore(numbers.start()));
            boolean attachedAfter = numbers.end() < withoutEmails.length()
                    && isAttached(withoutEmails.codePointAt(numbers.end()));
            if (!attachedBefore && !attachedAfter && PHONE.matcher(numbers.group()).matches()) {
                numbers.appendReplacement(result, "[số điện thoại]");
            }
        }
        return numbers.appendTail(result).toString();
    }

    private static boolean isAttached(int codePoint) {
        return ATTACHED.matcher(Character.toString(codePoint)).matches();
    }
}
