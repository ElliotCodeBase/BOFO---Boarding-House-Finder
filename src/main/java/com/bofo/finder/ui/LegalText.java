package com.bofo.finder.ui;

import com.bofo.finder.config.AppConfig;

import java.util.List;

/**
 * The text of the in-app legal documents. Each entry is {heading, paragraph}. Edit the wording here and raise
 * {@code policy.*.version} in config.properties when the meaning changes (the version is stored with each acceptance).
 * DRAFT written for the app's real behaviour; have it reviewed by a Philippine lawyer before public release.
 */
final class LegalText {
    private LegalText() {}

    static final String UPDATED = "Updated 25 October 2026";

    private static String contact() {
        String c = AppConfig.get("policy.contact").trim();
        return c.isEmpty() ? "the BOFO team through the app" : c;
    }

    static List<String[]> of(LegalOverlay.Doc d) {
        return switch (d) {
            case PRIVACY -> privacy();
            case TERMS -> terms();
            case AGREEMENTS -> agreements();
        };
    }

    private static List<String[]> privacy() {
        return List.of(
            new String[]{"", "BOFO Finder helps students find boarding houses near their college in Negros Oriental and Negros Occidental. This policy explains what we collect, why, and the choices you have under the Data Privacy Act of 2012 (RA 10173)."},
            new String[]{"What we collect", "Account: your name and email from Google sign-in, your phone number if you add it, and whether you use BOFO as a student or an owner.\nUsage: places you save, chats and messages, reviews, and reports you send.\nOwners: listing details and photos. To verify owners we also collect the front and back of a government ID and one live selfie."},
            new String[]{"Your location", "Only if you turn on \"Use my location\" (Profile > Preferences). It is used to sort places by distance and to plan walking, tricycle and jeepney trips. You can switch it off at any time. On a PC the location may be approximate."},
            new String[]{"Why we use it", "To run your account, show and rank listings, let students and owners chat, keep the community safe, process boost payments, and review owner verification. We do not sell your personal information."},
            new String[]{"Who can see it", "Other users see your name, profile photo and the listings or reviews you post. Chats are visible only to the people in them. Owner ID and selfie images are visible only to authorized BOFO admins."},
            new String[]{"Who helps us run the app", "Supabase (accounts, database, file storage), Google (sign-in), PayMongo (payments for listing boosts), and public map and route services (OpenStreetMap-based). They receive only what they need to do their job."},
            new String[]{"How long we keep it", "ID and selfie images are deleted after the verification review. Account data is kept while your account is active. When you delete your account, your listings, chats and verification images are removed."},
            new String[]{"Saved on your device", "To work with poor internet, the app keeps a copy of your chats, saved places and listing lists on your device. Signing out or deleting your account clears it."},
            new String[]{"Your rights", "You may ask to see, correct or delete your data, object to its use, and withdraw consent. Use Profile > \"Delete my account and data\" to remove it yourself, or contact " + contact() + ". You may also complain to the National Privacy Commission."},
            new String[]{"Children", "BOFO is for students and owners who can legally enter agreements. If you are under 18, use the app with a parent or guardian."},
            new String[]{"Changes", "If we change this policy in a way that matters, we will show the new version in the app and ask you to accept it again."}
        );
    }

    private static List<String[]> terms() {
        return List.of(
            new String[]{"", "These terms are the agreement between you and BOFO Finder. By signing in or using the app you accept them."},
            new String[]{"What BOFO is", "A listing and messaging service that connects students looking for boarding houses with owners. BOFO is not the landlord, not a party to any rental, and does not guarantee any listing, price or availability."},
            new String[]{"Your account", "Sign in with your own Google account and keep it secure. What you give us must be true. You are responsible for what happens under your account. You may be a student or an owner; switching modes is allowed, but owners must complete verification before listings go live."},
            new String[]{"Using the app", "Do not post false, misleading or illegal content, harass or threaten anyone, spam, scrape the app, try to break or bypass its security, or use it to cheat people out of money. Do not ask for or send payments outside the app in a way that hides them from the other party."},
            new String[]{"Listings and rentals", "Owners are responsible for the accuracy of their listings, photos, prices and house rules. Students should visit a place and confirm details before paying any deposit. Any rental agreement is only between the student and the owner."},
            new String[]{"Boosts and payments", "Optional paid boosts make a listing more visible for a set time. Payments go through PayMongo. A boost does not change a listing's quality checks and is not a guarantee of tenants. Refunds follow the rules shown at checkout."},
            new String[]{"Content you post", "You keep ownership of your photos, reviews and messages, and you allow BOFO to show them in the app for the purpose of running the service. You promise you have the right to post them."},
            new String[]{"Reports and removal", "We may hide content, remove listings, or suspend accounts that break these terms or the community guidelines, with or without notice when safety is at risk. You can ask for a review by contacting " + contact() + "."},
            new String[]{"No warranty, limited liability", "The app is provided \"as is\". Maps, walking times and fares are estimates from public data and may be wrong. To the extent allowed by law, BOFO is not liable for losses from a rental, from other users' acts, or from downtime."},
            new String[]{"Ending your account", "You can delete your account at any time in Profile. We may close accounts that seriously or repeatedly break these terms."},
            new String[]{"Governing law", "These terms follow the laws of the Republic of the Philippines."}
        );
    }

    private static List<String[]> agreements() {
        return List.of(
            new String[]{"", "These rules keep BOFO safe and fair for students and owners. They apply to everyone, and the owner agreement applies to anyone who posts a listing."},
            new String[]{"Community guidelines", "Be respectful and honest. No harassment, hate, threats, sexual content or discrimination. Reviews must come from real stays or visits and describe what really happened. Do not post other people's private information or photos without their permission. Report anything unsafe with the Report option."},
            new String[]{"Owner agreement: truthful listings", "Your photos must show the actual place. Price, deposit, advance, rules, water, power and Wi-Fi details must be correct and kept up to date. Mark a place unavailable when it is full. Do not list places you do not own or have no right to rent out."},
            new String[]{"Owner agreement: verification", "You confirm that the ID and selfie you submit are yours and genuine. Fake or someone else's documents lead to rejection and removal. Verification images are used only for the review and then deleted."},
            new String[]{"Owner agreement: fair dealing", "Treat every student equally and lawfully. Give a clear receipt for any deposit or advance and put the deposit terms in writing. Respect tenants' privacy and the safety standards that apply to dwellings (fire exits, safe wiring, clean water)."},
            new String[]{"Student guidelines", "Message owners politely and use the in-app chat for first contact. Visit the place before paying. Do not pay a deposit to anyone you could not verify, and report suspicious requests (for example, \"pay first, view later\")."},
            new String[]{"Messages and safety", "Chats can be reported or blocked. Meet in public places first, tell a friend where you are going, and never share passwords or one-time codes with anyone, including people who say they are from BOFO."},
            new String[]{"What happens if rules are broken", "We may warn you, hide content, remove a listing, suspend or close an account, and, where the law requires, share information with the authorities. Contact " + contact() + " if you think a decision was a mistake."}
        );
    }
}
