package com.smartgrocery.backend.service;

import com.smartgrocery.backend.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
public class AccountEmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.from:noreply@smartgrocery.vn}")
    private String fromAddress;

    public AccountEmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    // ── Ban/unban notification ────────────────────────────────────────────────

    public void sendBanStatusEmail(User user, String reason, boolean active) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) return;
        String subject = active ? "Tài khoản đã được mở khoá" : "Thông báo vô hiệu hoá tài khoản";
        String body = active
                ? "Tài khoản của bạn đã được kích hoạt lại.\nLý do: " + safe(reason)
                : "Tài khoản của bạn đã bị vô hiệu hoá.\nLý do: " + safe(reason);
        sendPlainText(user.getEmail(), subject, body);
    }

    // ── Email OTP verification ────────────────────────────────────────────────

    public void sendEmailVerificationOtp(String email, String fullName, String otp) {
        String subject = "SmartGrocery – Mã xác nhận đăng ký tài khoản";
        String html = buildOtpHtml(
                "Xác nhận tài khoản",
                "Xin chào " + safe(fullName) + ",",
                "Đây là mã xác nhận để kích hoạt tài khoản SmartGrocery của bạn:",
                otp,
                "Mã có hiệu lực trong <strong>10 phút</strong>. Vui lòng không chia sẻ mã này với bất kỳ ai.",
                "Nếu bạn không yêu cầu tạo tài khoản, hãy bỏ qua email này."
        );
        sendHtml(email, subject, html);
    }

    public void sendPasswordResetOtp(String email, String fullName, String otp) {
        String subject = "SmartGrocery – Mã xác nhận đặt lại mật khẩu";
        String html = buildOtpHtml(
                "Đặt lại mật khẩu",
                "Xin chào " + safe(fullName) + ",",
                "Đây là mã để đặt lại mật khẩu tài khoản SmartGrocery của bạn:",
                otp,
                "Mã có hiệu lực trong <strong>10 phút</strong>. Vui lòng không chia sẻ mã này với bất kỳ ai.",
                "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này và mật khẩu của bạn sẽ không thay đổi."
        );
        sendHtml(email, subject, html);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void sendHtml(String to, String subject, String htmlBody) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("[MAIL NOT CONFIGURED] Would send to={} subject=\"{}\"", to, subject);
            return;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(msg);
            log.info("[MAIL SENT] to={} subject=\"{}\"", to, subject);
        } catch (Exception e) {
            log.warn("[MAIL FAILED] to={}: {}", to, e.getMessage());
        }
    }

    private void sendPlainText(String to, String subject, String body) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.info("[MAIL NOT CONFIGURED] Skip email to {}", to);
            return;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("Could not send email to {}: {}", to, e.getMessage());
        }
    }

    private String buildOtpHtml(String title, String greeting, String intro, String otp, String note, String footer) {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body style="margin:0;padding:0;background:#f4f7f6;font-family:'Segoe UI',Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f7f6;padding:32px 0;">
                <tr><td align="center">
                  <table width="480" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);">
                    <tr><td style="background:linear-gradient(135deg,#16a34a,#22c55e);padding:32px 40px;text-align:center;">
                      <h1 style="margin:0;color:#fff;font-size:24px;font-weight:700;letter-spacing:-0.5px;">🛒 SmartGrocery</h1>
                      <p style="margin:8px 0 0;color:rgba(255,255,255,0.85);font-size:14px;">%s</p>
                    </td></tr>
                    <tr><td style="padding:40px 40px 32px;">
                      <p style="margin:0 0 12px;color:#1e293b;font-size:16px;">%s</p>
                      <p style="margin:0 0 28px;color:#475569;font-size:15px;line-height:1.6;">%s</p>
                      <div style="text-align:center;margin:28px 0;">
                        <div style="display:inline-block;background:#f0fdf4;border:2px solid #22c55e;border-radius:12px;padding:20px 48px;">
                          <span style="font-size:40px;font-weight:900;letter-spacing:12px;color:#16a34a;font-family:'Courier New',monospace;">%s</span>
                        </div>
                      </div>
                      <p style="margin:24px 0 0;color:#64748b;font-size:13px;line-height:1.6;">%s</p>
                    </td></tr>
                    <tr><td style="background:#f8fafc;padding:20px 40px;border-top:1px solid #e2e8f0;">
                      <p style="margin:0;color:#94a3b8;font-size:12px;line-height:1.6;">%s</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(title, greeting, intro, otp, note, footer);
    }

    private String safe(String s) {
        return s == null || s.isBlank() ? "bạn" : s.trim();
    }
}
