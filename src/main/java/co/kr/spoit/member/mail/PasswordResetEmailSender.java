package co.kr.spoit.member.mail;

/** Sends password-reset verification codes without exposing the transport to member services. */
public interface PasswordResetEmailSender {

    /** Returns whether all sender credentials and the configured sender address are usable. */
    boolean isConfigured();

    /** Sends a verification code that remains valid for the supplied number of minutes. */
    void sendCode(String email, String code, int expiresInMinutes);
}
