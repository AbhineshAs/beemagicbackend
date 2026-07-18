package com.beemagic.service;

import com.beemagic.entity.Order;
import com.beemagic.entity.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private ApplicationContext context;

    @Value("${app.frontend-url:https://beemagic.in}")
    private String frontendUrl;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.from:noreply@beemagic.com}")
    private String mailFrom;

    public void sendOrderConfirmationEmail(Order order) {
        Object mailSender = null;
        try {
            mailSender = context.getBean("mailSender");
        } catch (Exception e) {
            // mailSender bean is not present or configured
        }

        if (mailSender == null) {
            logger.warn("SMTP email sender bean is not available. Order confirmation email was skipped. Order ID: {}", order.getId());
            return;
        }

        try {
            // create MimeMessage: MimeMessage message = mailSender.createMimeMessage();
            Method createMimeMessageMethod = mailSender.getClass().getMethod("createMimeMessage");
            Object mimeMessage = createMimeMessageMethod.invoke(mailSender);

            // load MimeMessageHelper and construct it:
            // MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            Class<?> mimeMessageHelperClass = Class.forName("org.springframework.mail.javamail.MimeMessageHelper");
            Class<?> mimeMessageClass = Class.forName("jakarta.mail.internet.MimeMessage");
            Constructor<?> helperConstructor = mimeMessageHelperClass.getConstructor(mimeMessageClass, int.class, String.class);
            
            // MULTIPART_MODE_MIXED_RELATED is 3
            Object helper = helperConstructor.newInstance(mimeMessage, 3, StandardCharsets.UTF_8.name());

            // helper.setTo(order.getUser().getEmail());
            Method setToMethod = mimeMessageHelperClass.getMethod("setTo", String.class);
            setToMethod.invoke(helper, order.getUser().getEmail());

            // helper.setFrom(fromAddress);
            Method setFromMethod = mimeMessageHelperClass.getMethod("setFrom", String.class);
            String fromAddress = (mailUsername != null && !mailUsername.trim().isEmpty()) ? mailUsername : mailFrom;
            setFromMethod.invoke(helper, fromAddress);

            // helper.setSubject(...)
            Method setSubjectMethod = mimeMessageHelperClass.getMethod("setSubject", String.class);
            setSubjectMethod.invoke(helper, "Bee Magic - Order Confirmed! (Order #" + order.getId() + ")");

            // helper.setText(htmlContent, true);
            Method setTextMethod = mimeMessageHelperClass.getMethod("setText", String.class, boolean.class);
            String htmlContent = buildOrderEmailTemplate(order);
            setTextMethod.invoke(helper, htmlContent, true);

            // mailSender.send(mimeMessage);
            Method sendMethod = mailSender.getClass().getMethod("send", mimeMessageClass);
            sendMethod.invoke(mailSender, mimeMessage);

            logger.info("Order confirmation email sent successfully for Order ID: {}", order.getId());
        } catch (Exception e) {
            logger.error("Failed to send order confirmation email for Order ID: {}", order.getId(), e);
        }
    }

    private String buildOrderEmailTemplate(Order order) {
        StringBuilder itemsTable = new StringBuilder();
        for (OrderItem item : order.getItems()) {
            itemsTable.append("<tr>")
                    .append("<td style='padding: 12px; border-bottom: 1px solid #e7e5e4;'>")
                    .append("<div style='font-weight: 600; color: #1c1917;'>").append(item.getName()).append("</div>")
                    .append("</td>")
                    .append("<td style='padding: 12px; border-bottom: 1px solid #e7e5e4; text-align: center; color: #78716c;'>")
                    .append(item.getQuantity())
                    .append("</td>")
                    .append("<td style='padding: 12px; border-bottom: 1px solid #e7e5e4; text-align: right; font-weight: 600; color: #1c1917;'>")
                    .append("\u20B9").append(String.format("%.2f", item.getPrice() * item.getQuantity()))
                    .append("</td>")
                    .append("</tr>");
        }

        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset='utf-8'>" +
                "<title>Order Confirmation</title>" +
                "</head>" +
                "<body style='font-family: \"Inter\", sans-serif; background-color: #fafaf9; margin: 0; padding: 40px 20px; -webkit-font-smoothing: antialiased;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 16px; border: 1px solid #e7e5e4; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);'>" +
                // Header
                "<div style='background: linear-gradient(135deg, #d97706, #b45309); padding: 32px 24px; text-align: center; color: #ffffff;'>" +
                "<h1 style='margin: 0; font-size: 28px; font-weight: 800; letter-spacing: -0.5px;'>Thank You For Your Order!</h1>" +
                "<p style='margin: 8px 0 0 0; font-size: 16px; opacity: 0.9;'>We're preparing your Bee Magic selection.</p>" +
                "</div>" +
                // Body
                "<div style='padding: 32px 24px;'>" +
                "<p style='margin: 0 0 24px 0; font-size: 16px; line-height: 1.6; color: #44403c;'>Hi " + order.getUser().getName() + ",</p>" +
                "<p style='margin: 0 0 24px 0; font-size: 16px; line-height: 1.6; color: #44403c;'>Your order has been successfully placed! Below are your order summary and tracking details.</p>" +
                // Order Info Box
                "<div style='background-color: #f5f5f4; border-radius: 12px; padding: 20px; margin-bottom: 28px; border: 1px solid #e7e5e4;'>" +
                "<div style='margin-bottom: 8px; font-size: 14px; color: #78716c;'>Order ID: <strong style='color: #1c1917;'>" + order.getId() + "</strong></div>" +
                "<div style='margin-bottom: 8px; font-size: 14px; color: #78716c;'>Payment Method: <strong style='color: #1c1917;'>" + order.getPaymentMethod() + "</strong></div>" +
                "<div style='font-size: 14px; color: #78716c;'>Shipping Address: <strong style='color: #1c1917;'>" + order.getShippingAddress() + "</strong></div>" +
                "</div>" +
                // Items Table
                "<h3 style='margin: 0 0 16px 0; font-size: 18px; font-weight: 700; color: #1c1917; border-bottom: 2px solid #e7e5e4; padding-bottom: 8px;'>Order Details</h3>" +
                "<table style='width: 100%; border-collapse: collapse; margin-bottom: 24px;'>" +
                "<thead>" +
                "<tr>" +
                "<th style='text-align: left; padding: 12px; font-size: 14px; color: #78716c; border-bottom: 1px solid #e7e5e4;'>Product</th>" +
                "<th style='text-align: center; padding: 12px; font-size: 14px; color: #78716c; border-bottom: 1px solid #e7e5e4;'>Qty</th>" +
                "<th style='text-align: right; padding: 12px; font-size: 14px; color: #78716c; border-bottom: 1px solid #e7e5e4;'>Price</th>" +
                "</tr>" +
                "</thead>" +
                "<tbody>" +
                itemsTable.toString() +
                "</tbody>" +
                "</table>" +
                // Total
                "<div style='text-align: right; margin-bottom: 32px;'>" +
                "<span style='font-size: 16px; color: #78716c; margin-right: 12px;'>Total Paid:</span>" +
                "<span style='font-size: 24px; font-weight: 800; color: #d97706;'>\u20B9" + String.format("%.2f", order.getTotalAmount()) + "</span>" +
                "</div>" +
                // CTA Button
                "<div style='text-align: center; margin-bottom: 12px;'>" +
                "<a href='" + frontendUrl + "/track-order' style='display: inline-block; background: linear-gradient(135deg, #d97706, #b45309); color: #ffffff; text-decoration: none; padding: 16px 32px; font-size: 16px; font-weight: 700; border-radius: 12px; box-shadow: 0 4px 6px -1px rgba(217, 119, 6, 0.2); transition: transform 0.2s;'>Track Your Order</a>" +
                "</div>" +
                "</div>" +
                // Footer
                "<div style='background-color: #f5f5f4; padding: 24px; text-align: center; font-size: 14px; color: #78716c; border-top: 1px solid #e7e5e4;'>" +
                "<p style='margin: 0 0 8px 0;'>If you have any questions, reply to this email or contact support.</p>" +
                "<p style='margin: 0; font-weight: 600; color: #1c1917;'>© 2026 Bee Magic. All rights reserved.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }
}
