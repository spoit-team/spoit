package co.kr.spoit.member.mail;

import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Gmail API sender credentials supplied by the runtime environment. */
@ConfigurationProperties(prefix = "spoit.gmail")
public class GmailMailProperties {

    private static final Pattern ADDRESS_PATTERN = Pattern.compile(
            "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@"
                    + "(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+"
                    + "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$");

    private String clientId = "";
    private String clientSecret = "";
    private String refreshToken = "";
    private String sender = "";

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public boolean isConfigured() {
        return hasValue(clientId) && hasValue(clientSecret) && hasValue(refreshToken)
                && isValidEmailAddress(sender);
    }

    static boolean isValidEmailAddress(String value) {
        return hasValue(value) && value.length() <= 254 && ADDRESS_PATTERN.matcher(value).matches();
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }
}
