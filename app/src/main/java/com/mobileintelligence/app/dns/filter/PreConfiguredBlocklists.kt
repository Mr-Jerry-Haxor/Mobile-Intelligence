package com.mobileintelligence.app.dns.filter

import com.mobileintelligence.app.dns.data.entity.BlocklistEntity

/**
 * Pre-configured community blocklists from popular Pi-hole sources.
 * Users can enable any combination of these from the Blocklist Management screen.
 */
object PreConfiguredBlocklists {

    data class BlocklistSource(
        val name: String,
        val description: String,
        val url: String,
        val category: String
    )

    val SOURCES: List<BlocklistSource> = listOf(
        // ─── Ad Blocking ────────────────────────────────────────
        BlocklistSource(
            name = "StevenBlack Hosts",
            description = "Unified hosts file with base extensions — ads & malware",
            url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            category = "ads"
        ),
        BlocklistSource(
            name = "AdAway Default Blocklist",
            description = "AdAway default host file for ad blocking",
            url = "https://adaway.org/hosts.txt",
            category = "ads"
        ),
        BlocklistSource(
            name = "Disconnect Simple Ad",
            description = "Disconnect.me simple advertising filter list",
            url = "https://s3.amazonaws.com/lists.disconnect.me/simple_ad.txt",
            category = "ads"
        ),
        BlocklistSource(
            name = "RPiList EasyList",
            description = "EasyList domains converted for DNS blocking",
            url = "https://raw.githubusercontent.com/RPiList/specials/master/Blocklisten/easylist",
            category = "ads"
        ),

        // ─── Trackers & Privacy ─────────────────────────────────
        BlocklistSource(
            name = "Hagezi Pro Plus",
            description = "Multi PRO++ — comprehensive ad/tracker/malware blocklist",
            url = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/pro.plus.txt",
            category = "trackers"
        ),
        BlocklistSource(
            name = "Frogeye First-Party Trackers",
            description = "First-party CNAME tracker domains",
            url = "https://hostfiles.frogeye.fr/firstparty-trackers-hosts.txt",
            category = "trackers"
        ),
        BlocklistSource(
            name = "AdGuard SmartTV Tracking",
            description = "AdGuard filter for Smart TV tracking domains",
            url = "https://adguardteam.github.io/HostlistsRegistry/assets/filter_7.txt",
            category = "trackers"
        ),

        // ─── Malware & Threats ──────────────────────────────────
        BlocklistSource(
            name = "URLHaus Malicious URLs",
            description = "Abuse.ch URLHaus known malware distribution hosts",
            url = "https://urlhaus.abuse.ch/downloads/hostfile/",
            category = "malware"
        ),
        BlocklistSource(
            name = "ThreatFox IOC Hosts",
            description = "Abuse.ch ThreatFox indicators of compromise",
            url = "https://threatfox.abuse.ch/downloads/hostfile/",
            category = "malware"
        ),
        BlocklistSource(
            name = "RPiList Malware",
            description = "RPiList known malware domains",
            url = "https://raw.githubusercontent.com/RPiList/specials/master/Blocklisten/malware",
            category = "malware"
        ),
        BlocklistSource(
            name = "Disconnect Malvertising",
            description = "Disconnect.me malvertising filter list",
            url = "https://s3.amazonaws.com/lists.disconnect.me/simple_malvertising.txt",
            category = "malware"
        ),
        BlocklistSource(
            name = "Hagezi Threat Intelligence Feeds",
            description = "Actively harmful domains — threat intelligence aggregation",
            url = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/tif.txt",
            category = "malware"
        ),
        BlocklistSource(
            name = "Hagezi Badware Hoster",
            description = "Hosting providers with predominantly malicious content",
            url = "https://gitlab.com/hagezi/mirror/-/raw/main/dns-blocklists/adblock/hoster.txt",
            category = "malware"
        ),
        BlocklistSource(
            name = "UBlock Badware Risks",
            description = "AdGuard-hosted uBlock badware risk domains",
            url = "https://adguardteam.github.io/HostlistsRegistry/assets/filter_50.txt",
            category = "malware"
        ),

        // ─── Comprehensive ──────────────────────────────────────
        BlocklistSource(
            name = "OISD Big",
            description = "OISD Big — merged and deduplicated internet-wide blocklist",
            url = "https://big.oisd.nl",
            category = "ads"
        ),

        // ─── Specialized ────────────────────────────────────────
        BlocklistSource(
            name = "StevenBlack Fake News",
            description = "StevenBlack extension — fake news domains",
            url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/extensions/fakenews/hosts",
            category = "privacy"
        ),
        BlocklistSource(
            name = "Hagezi Most Abused TLDs",
            description = "Blocks spam and abusive top-level domains",
            url = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/spam-tlds-adblock.txt",
            category = "privacy"
        ),
        BlocklistSource(
            name = "Hagezi Gambling",
            description = "Block gambling and betting domains",
            url = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/gambling.txt",
            category = "gambling"
        ),
        BlocklistSource(
            name = "Prigent Crypto",
            description = "Block cryptojacking and crypto-mining domains",
            url = "https://v.firebog.net/hosts/Prigent-Crypto.txt",
            category = "crypto"
        ),
        BlocklistSource(
            name = "BlocklistProject Crypto",
            description = "BlocklistProject crypto-mining filter",
            url = "https://raw.githubusercontent.com/blocklistproject/Lists/master/crypto.txt",
            category = "crypto"
        )
    )

    /**
     * Convert pre-configured sources to Room entities.
     */
    fun toEntities(): List<BlocklistEntity> = SOURCES.map { source ->
        BlocklistEntity(
            name = source.name,
            description = source.description,
            url = source.url,
            category = source.category,
            isEnabled = false,
            isBuiltIn = true
        )
    }
}
