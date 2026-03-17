package com.mobileintelligence.app.engine.dns

/**
 * Domain Category Engine — classifies domains into meaningful categories
 * for analytics, correlation, and UI display.
 *
 * Categories:
 * - ADS: Advertising networks and ad-serving domains
 * - ANALYTICS: Analytics, telemetry, and measurement
 * - SOCIAL: Social media and messaging platforms
 * - MALWARE: Known malicious, phishing, or scam domains
 * - CDN: Content delivery networks
 * - PUSH: Push notification services
 * - SEARCH: Search engines
 * - STREAMING: Video/music/media streaming
 * - SHOPPING: E-commerce and shopping
 * - GAMING: Game services and platforms
 * - PRODUCTIVITY: Work and productivity tools
 * - SYSTEM: OS-level and device services
 * - UNKNOWN: Cannot be categorized
 */
class DomainCategoryEngine {

    enum class DomainCategory(val displayName: String, val emoji: String) {
        ADS("Advertising", "\uD83D\uDCE2"),
        ANALYTICS("Analytics", "\uD83D\uDCCA"),
        SOCIAL("Social Media", "\uD83D\uDC65"),
        MALWARE("Malware", "\u26A0\uFE0F"),
        CDN("CDN", "\uD83C\uDF10"),
        PUSH("Push Notifications", "\uD83D\uDD14"),
        SEARCH("Search", "\uD83D\uDD0D"),
        STREAMING("Streaming", "\uD83C\uDFA5"),
        SHOPPING("Shopping", "\uD83D\uDED2"),
        GAMING("Gaming", "\uD83C\uDFAE"),
        PRODUCTIVITY("Productivity", "\uD83D\uDCBC"),
        SYSTEM("System", "\u2699\uFE0F"),
        EMAIL("Email", "\uD83D\uDCE7"),
        FINANCE("Finance", "\uD83D\uDCB3"),
        NEWS("News", "\uD83D\uDCF0"),
        UNKNOWN("Other", "\u2753");

        companion object {
            fun fromString(name: String): DomainCategory =
                entries.find { it.name.equals(name, ignoreCase = true) } ?: UNKNOWN
        }
    }

    data class CategoryResult(
        val category: DomainCategory,
        val confidence: Float, // 0.0 - 1.0
        val subcategory: String? = null
    )

    // Pattern-based categorization rules
    private val categoryRules: List<CategoryRule> = buildCategoryRules()

    /**
     * Categorize a domain into one of the predefined categories.
     * Uses keyword matching on domain parts and known domain databases.
     */
    fun categorize(domain: String): DomainCategory {
        val normalized = domain.lowercase().trimEnd('.')

        // Check exact/suffix matches first (high confidence)
        for (rule in categoryRules) {
            if (rule.matches(normalized)) {
                return rule.category
            }
        }

        // Keyword-based heuristics (lower confidence)
        return inferFromKeywords(normalized)
    }

    /**
     * Categorize with confidence score.
     */
    fun categorizeDetailed(domain: String): CategoryResult {
        val normalized = domain.lowercase().trimEnd('.')

        for (rule in categoryRules) {
            if (rule.matches(normalized)) {
                return CategoryResult(rule.category, rule.confidence, rule.subcategory)
            }
        }

        val inferred = inferFromKeywords(normalized)
        return CategoryResult(inferred, if (inferred != DomainCategory.UNKNOWN) 0.4f else 0f)
    }

    /**
     * Batch categorize multiple domains efficiently.
     */
    fun categorizeBatch(domains: List<String>): Map<String, DomainCategory> =
        domains.associateWith { categorize(it) }

    private fun inferFromKeywords(domain: String): DomainCategory {
        val parts = domain.split(".")

        // Keyword scanning
        for (part in parts) {
            when {
                part.contains("ad") && (part.contains("server") || part.contains("deliver") ||
                        part == "ads" || part == "ad" || part == "adservice") -> return DomainCategory.ADS
                part.contains("track") || part.contains("beacon") ||
                        part.contains("telemetry") || part.contains("metric") -> return DomainCategory.ANALYTICS
                part.contains("analytics") || part.contains("stats") ||
                        part.contains("measure") || part.contains("collect") -> return DomainCategory.ANALYTICS
                part.contains("push") || part.contains("notify") ||
                        part.contains("notification") -> return DomainCategory.PUSH
                part.contains("cdn") || part.contains("static") ||
                        part.contains("cache") || part.contains("edge") -> return DomainCategory.CDN
                part.contains("stream") || part.contains("video") ||
                        part.contains("media") || part.contains("play") -> return DomainCategory.STREAMING
                part.contains("shop") || part.contains("store") ||
                        part.contains("cart") || part.contains("buy") -> return DomainCategory.SHOPPING
                part.contains("game") || part.contains("gaming") ||
                        part.contains("play") -> return DomainCategory.GAMING
                part.contains("mail") || part.contains("smtp") ||
                        part.contains("imap") -> return DomainCategory.EMAIL
                part.contains("news") || part.contains("feed") -> return DomainCategory.NEWS
                part.contains("bank") || part.contains("pay") ||
                        part.contains("finance") -> return DomainCategory.FINANCE
            }
        }

        return DomainCategory.UNKNOWN
    }

    // ── Category Rule Definitions ───────────────────────────────

    private data class CategoryRule(
        val category: DomainCategory,
        val suffixes: List<String> = emptyList(),
        val contains: List<String> = emptyList(),
        val exact: List<String> = emptyList(),
        val confidence: Float = 0.9f,
        val subcategory: String? = null
    ) {
        fun matches(domain: String): Boolean {
            if (exact.any { domain == it }) return true
            if (suffixes.any { domain.endsWith(it) || domain == it.removePrefix(".") }) return true
            if (contains.any { domain.contains(it) }) return true
            return false
        }
    }

    private fun buildCategoryRules(): List<CategoryRule> = listOf(
        // ── Advertising ─────────────────────────────────────
        CategoryRule(
            category = DomainCategory.ADS,
            suffixes = listOf(
                ".doubleclick.net", ".googlesyndication.com", ".googleadservices.com",
                ".moatads.com", ".adnxs.com", ".adsrvr.org", ".criteo.com",
                ".taboola.com", ".outbrain.com", ".pubmatic.com", ".rubiconproject.com",
                ".openx.net", ".appnexus.com", ".bidswitch.net", ".smaato.net"
            ),
            exact = listOf("pagead2.googlesyndication.com", "adservice.google.com"),
            subcategory = "ad_network"
        ),

        // ── Analytics ───────────────────────────────────────
        CategoryRule(
            category = DomainCategory.ANALYTICS,
            suffixes = listOf(
                ".google-analytics.com", ".analytics.google.com",
                ".app-measurement.com", ".crashlytics.com",
                ".appsflyer.com", ".branch.io", ".adjust.com",
                ".amplitude.com", ".mixpanel.com", ".segment.io",
                ".hotjar.com", ".fullstory.com", ".heap.io",
                ".newrelic.com", ".datadoghq.com", ".sentry.io"
            ),
            contains = listOf("analytics", "telemetry", "metrics"),
            subcategory = "analytics"
        ),

        // ── Social Media ────────────────────────────────────
        CategoryRule(
            category = DomainCategory.SOCIAL,
            suffixes = listOf(
                ".facebook.com", ".fbcdn.net", ".instagram.com", ".twitter.com",
                ".x.com", ".tiktok.com", ".tiktokcdn.com", ".snapchat.com",
                ".linkedin.com", ".reddit.com", ".pinterest.com", ".tumblr.com",
                ".whatsapp.com", ".whatsapp.net", ".telegram.org", ".discord.com",
                ".discordapp.com", ".signal.org", ".threads.net"
            ),
            subcategory = "social"
        ),

        // ── Search ──────────────────────────────────────────
        CategoryRule(
            category = DomainCategory.SEARCH,
            suffixes = listOf(
                ".google.com", ".googleapis.com", ".bing.com",
                ".duckduckgo.com", ".yahoo.com", ".baidu.com",
                ".yandex.com", ".brave.com"
            ),
            subcategory = "search"
        ),

        // ── Streaming ───────────────────────────────────────
        CategoryRule(
            category = DomainCategory.STREAMING,
            suffixes = listOf(
                ".youtube.com", ".ytimg.com", ".googlevideo.com",
                ".netflix.com", ".nflxvideo.net", ".spotify.com",
                ".scdn.co", ".twitch.tv", ".hulu.com", ".disneyplus.com",
                ".primevideo.com", ".hbomax.com", ".crunchyroll.com",
                ".soundcloud.com", ".vimeo.com", ".dailymotion.com"
            ),
            subcategory = "media"
        ),

        // ── Shopping ────────────────────────────────────────
        CategoryRule(
            category = DomainCategory.SHOPPING,
            suffixes = listOf(
                ".amazon.com", ".amazon.in", ".ebay.com", ".etsy.com",
                ".shopify.com", ".aliexpress.com", ".walmart.com",
                ".flipkart.com", ".myntra.com"
            ),
            subcategory = "ecommerce"
        ),

        // ── Gaming ──────────────────────────────────────────
        CategoryRule(
            category = DomainCategory.GAMING,
            suffixes = listOf(
                ".steampowered.com", ".steamcommunity.com",
                ".epicgames.com", ".ea.com", ".origin.com",
                ".riotgames.com", ".blizzard.com", ".unity3d.com",
                ".unrealengine.com", ".playstationnetwork.com"
            ),
            subcategory = "game_platform"
        ),

        // ── Productivity ────────────────────────────────────
        CategoryRule(
            category = DomainCategory.PRODUCTIVITY,
            suffixes = listOf(
                ".microsoft.com", ".office.com", ".office365.com",
                ".outlook.com", ".live.com", ".slack.com", ".notion.so",
                ".figma.com", ".github.com", ".gitlab.com",
                ".atlassian.com", ".jira.com", ".trello.com",
                ".zoom.us", ".webex.com", ".teams.microsoft.com"
            ),
            subcategory = "work"
        ),

        // ── System ──────────────────────────────────────────
        CategoryRule(
            category = DomainCategory.SYSTEM,
            suffixes = listOf(
                ".gstatic.com", ".googleapis.com",
                ".apple.com", ".icloud.com", ".mzstatic.com",
                ".android.com", ".gvt1.com", ".gvt2.com",
                ".windowsupdate.com", ".wns.windows.com"
            ),
            subcategory = "os_service"
        ),

        // ── CDN ─────────────────────────────────────────────
        CategoryRule(
            category = DomainCategory.CDN,
            suffixes = listOf(
                ".cloudflare.com", ".cloudfront.net", ".akamaized.net",
                ".akamai.net", ".fastly.net", ".edgekey.net",
                ".cdninstagram.com", ".fbcdn.net", ".gstatic.com",
                ".jsdelivr.net", ".unpkg.com", ".bootstrapcdn.com"
            ),
            subcategory = "cdn"
        ),

        // ── Email ───────────────────────────────────────────
        CategoryRule(
            category = DomainCategory.EMAIL,
            suffixes = listOf(
                ".gmail.com", ".mail.google.com", ".protonmail.com",
                ".tutanota.com", ".mailgun.com", ".sendgrid.net",
                ".zoho.com"
            ),
            subcategory = "email_service"
        ),

        // ── Finance ─────────────────────────────────────────
        CategoryRule(
            category = DomainCategory.FINANCE,
            suffixes = listOf(
                ".paypal.com", ".stripe.com", ".razorpay.com",
                ".visa.com", ".mastercard.com", ".gpay.com"
            ),
            subcategory = "payment"
        ),

        // ── Push ────────────────────────────────────────────
        CategoryRule(
            category = DomainCategory.PUSH,
            suffixes = listOf(
                ".firebase.google.com", ".fcm.googleapis.com",
                ".push.apple.com", ".onesignal.com", ".pusher.com",
                ".pushwoosh.com"
            ),
            subcategory = "push_service"
        )
    )
}
