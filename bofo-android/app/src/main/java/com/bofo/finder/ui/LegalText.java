package com.bofo.finder.ui;

import com.bofo.finder.config.AppConfig;

/**
 * Short in-app versions of the agreements. The version numbers come from assets/config.properties (policy.*.version) and are what
 * users.user_agreements records. NOTE: the original desktop LegalText.java was not in the zips this stage was built from, so
 * these texts were written fresh: replace the strings below with the desktop wording (and have them reviewed) before release.
 */
public final class LegalText {
    private LegalText() {}
    public enum Doc { TERMS, PRIVACY, OWNER }

    public static String title(Doc d) {
        switch (d) {
            case PRIVACY: return "Privacy Policy";
            case OWNER: return "Owner Agreement";
            default: return "Terms of Use";
        }
    }

    public static String version(Doc d) {
        String key = d == Doc.PRIVACY ? "policy.privacy.version" : d == Doc.OWNER ? "policy.owner.version" : "policy.terms.version";
        String v = AppConfig.get(key).trim();
        return v.isEmpty() ? "1.0" : v;
    }

    private static String contact() {
        String c = AppConfig.get("policy.contact").trim();
        return c.isEmpty() ? "the BOFO team through the app" : c;
    }

    public static String body(Doc d) {
        String head = title(d) + " - version " + version(d) + "\n\n";
        switch (d) {
            case PRIVACY:
                return head
                        + "What we collect\nYour sign-in e-mail, name, optional username, phone, age, sex, region and college, your profile photo, your chats with owners or students, your saved places and reviews. Owners also submit an ID photo and a live selfie for verification.\n\n"
                        + "Why\nTo show boarding houses near you, let students and owners talk, keep the community safe, and review owner identity. We do not sell your data.\n\n"
                        + "Location\nOnly used when you allow it and keep \"Use my location\" on in Settings. You can turn it off at any time.\n\n"
                        + "ID and selfie\nVerification photos are stored in a private area and are seen only by BOFO administrators who review them.\n\n"
                        + "BOFO AI chats\nConversations with the BOFO AI assistant (your questions and its answers, with your name and e-mail) are stored and can be reviewed by BOFO administrators, and they are deleted automatically after 90 days.\n\n"   // v2.0.91 AI Chats monitor: REMOVE this line with the feature
                        + "Your choices\nYou can edit your profile in Settings, and delete your account there. Deleting removes your profile, chats and listings.\n\n"
                        + "Contact\nQuestions about privacy: " + contact() + ".";
            case OWNER:
                return head
                        + "By listing as an owner you confirm that\n- you are the owner or are allowed to rent out each place you list;\n- the photos, prices, address and available beds are true and kept up to date;\n- the ID and selfie you submit are your own;\n- you will treat students fairly and answer them respectfully.\n\n"
                        + "BOFO may remove a listing or suspend an account that breaks these rules or that students report as misleading.\n\n"
                        + "Boosts (paid promotion) do not guarantee bookings and are not refundable once the boost has started, unless the law says otherwise.\n\n"
                        + "Contact: " + contact() + ".";
            default:
                return head
                        + "Using BOFO\nBOFO helps students find boarding houses and talk to owners. BOFO is not a party to any rental agreement between a student and an owner.\n\n"
                        + "Your account\nKeep your sign-in private. Give true information. You must be old enough to agree to these terms where you live.\n\n"
                        + "Behaviour\nNo harassment, scams, fake reviews or fake listings. We may remove content and suspend accounts that break these rules.\n\n"
                        + "Chats and reviews\nWhat you post may be seen by the person you are talking to or, for reviews, by other users.\n\n"
                        + "AI assistant\nThe BOFO mascot is an AI helper. It can be wrong: check prices and details with the owner before you decide.\n\n"
                        + "Changes\nWe may update these terms. When the version changes you will be asked to accept it again.\n\n"
                        + "Contact: " + contact() + ".";
        }
    }
}
