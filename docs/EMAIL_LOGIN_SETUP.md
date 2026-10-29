# Email login setup (Supabase) - v1.20.0

The app signs in with a 6-digit code from the email (or email + password). Nothing opens in a browser.
Supabase sends the email, so its template must contain the code.

## 1. Email provider
Supabase > Authentication > Sign In / Providers > **Email**: enabled. Leave "Confirm email" ON (recommended).

## 2. Email templates (required - the default ones only contain a link)
Supabase > Authentication > Email Templates. In BOTH **Magic Link** and **Confirm signup**, replace the body with:

```html
<h2>Your BOFO code</h2>
<p>Type this code in the app:</p>
<p style="font-size:28px;letter-spacing:6px"><b>{{ .Token }}</b></p>
<p>It expires in one hour. If you did not ask for it, ignore this email.</p>
```

## 3. Sending limit (important before real users)
Supabase's built-in email sender allows only a few emails per hour and only to your team's addresses.
Set up **custom SMTP** (Authentication > Emails > SMTP Settings; Resend, Brevo or Gmail SMTP all work) so any student can get a code.
Optional: Authentication > Rate Limits > email sent per hour.

## 4. Code length / expiry (optional)
Authentication > Sign In / Providers > Email: "Email OTP Length" (default 6) and "Email OTP Expiration" (default 3600 s).
The app accepts 6 to 10 digits.

## Errors
| Message in the app | Cause |
|---|---|
| "Too many tries. Wait a minute" | Email rate limit hit (see step 3). |
| Email arrives with a link and no code | Template from step 2 not saved. |
| "That code is wrong or has expired" | Old code (a newer one replaces it) or a typo. |
| "Email sign-in is turned off" | Email provider disabled (step 1). |
