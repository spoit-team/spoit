package co.kr.spoit.member.mail;

import java.util.regex.Pattern;

/**
 * A transport failure with a safe message that never includes provider responses or request data.
 *
 * <p>Diagnostics are restricted to a bounded machine token from the provider JSON
 * ({@code error} or {@code error.errors[0].reason}), an HTTP status, and a cause class
 * simple name. Free-form provider text such as {@code error_description} is never retained.</p>
 */
public class PasswordResetEmailException extends RuntimeException {

    private static final Pattern SAFE_TOKEN = Pattern.compile("^[A-Za-z0-9._$-]{1,64}$");

    private final String code;
    private final Integer httpStatus;
    private final String providerReason;
    private final String causeType;

    public PasswordResetEmailException(String code, String message) {
        this(code, message, null, null, null);
    }

    public PasswordResetEmailException(String code, String message, Integer httpStatus,
            String providerReason, String causeType) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.providerReason = safeToken(providerReason);
        this.causeType = safeToken(causeType);
    }

    public String getCode() {
        return code;
    }

    /** HTTP status of the failed provider call, or {@code null} when no response was received. */
    public Integer getHttpStatus() {
        return httpStatus;
    }

    /** Bounded provider machine token, or {@code null} when absent or not a safe token. */
    public String getProviderReason() {
        return providerReason;
    }

    /** Simple class name of the transport cause, or {@code null} when unknown. */
    public String getCauseType() {
        return causeType;
    }

    private static String safeToken(String value) {
        return value != null && SAFE_TOKEN.matcher(value).matches() ? value : null;
    }
}
