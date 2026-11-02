package com.waylo.apigateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("waylo.gateway")
public class RateLimitProperties {

    /** Honor X-Forwarded-For for the client IP. Off by default because the header is spoofable. */
    private boolean trustForwardedFor = false;
    private int loginPerMinute = 10;
    private int registerPerMinute = 5;
    private int forgotPasswordPerMinute = 3;
    private int resendVerificationPerMinute = 3;
    private int refreshPerMinute = 30;
    private int orderCreatePerMinute = 30;
    private int voucherRedeemPerMinute = 30;

    public boolean isTrustForwardedFor() { return trustForwardedFor; }
    public void setTrustForwardedFor(boolean v) { this.trustForwardedFor = v; }
    public int getLoginPerMinute() { return loginPerMinute; }
    public void setLoginPerMinute(int v) { this.loginPerMinute = v; }
    public int getRegisterPerMinute() { return registerPerMinute; }
    public void setRegisterPerMinute(int v) { this.registerPerMinute = v; }
    public int getForgotPasswordPerMinute() { return forgotPasswordPerMinute; }
    public void setForgotPasswordPerMinute(int v) { this.forgotPasswordPerMinute = v; }
    public int getResendVerificationPerMinute() { return resendVerificationPerMinute; }
    public void setResendVerificationPerMinute(int v) { this.resendVerificationPerMinute = v; }
    public int getRefreshPerMinute() { return refreshPerMinute; }
    public void setRefreshPerMinute(int v) { this.refreshPerMinute = v; }
    public int getOrderCreatePerMinute() { return orderCreatePerMinute; }
    public void setOrderCreatePerMinute(int v) { this.orderCreatePerMinute = v; }
    public int getVoucherRedeemPerMinute() { return voucherRedeemPerMinute; }
    public void setVoucherRedeemPerMinute(int v) { this.voucherRedeemPerMinute = v; }
}
