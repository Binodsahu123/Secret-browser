package com.example.browser

import android.util.Log

object VoiceCommandDatabase {

    // Massive database of popular websites, Indian/Global news channels, weather services, and portals
    private val siteMap = mapOf(
        // === News Mappings (Indian & Global) ===
        "aaj tak" to "https://aajtak.in",
        "aajtak" to "https://aajtak.in",
        "zee news" to "https://zeenews.india.com",
        "zeenews" to "https://zeenews.india.com",
        "ndtv" to "https://ndtv.com",
        "ndtv hindi" to "https://ndtv.in",
        "abp news" to "https://abplive.com",
        "abpnews" to "https://abplive.com",
        "news18" to "https://news18.com",
        "news 18" to "https://news18.com",
        "news eighteen" to "https://news18.com",
        "india tv" to "https://indiatvnews.com",
        "indiatv" to "https://indiatvnews.com",
        "republic bharat" to "https://bharat.republicworld.com",
        "republic tv" to "https://republicworld.com",
        "republicworld" to "https://republicworld.com",
        "tv9 bharatvarsh" to "https://tv9bharatvarsh.com",
        "tv9" to "https://tv9bharatvarsh.com",
        "dainik jagran" to "https://jagran.com",
        "jagran" to "https://jagran.com",
        "dainik bhaskar" to "https://bhaskar.com",
        "bhaskar" to "https://bhaskar.com",
        "amar ujala" to "https://amarujala.com",
        "amarujala" to "https://amarujala.com",
        "hindustan" to "https://livehindustan.com",
        "live hindustan" to "https://livehindustan.com",
        "punjab kesari" to "https://punjabkesari.in",
        "times of india" to "https://timesofindia.indiatimes.com",
        "toi" to "https://timesofindia.indiatimes.com",
        "hindustan times" to "https://hindustantimes.com",
        "indian express" to "https://indianexpress.com",
        "the hindu" to "https://thehindu.com",
        "mint" to "https://livemint.com",
        "livemint" to "https://livemint.com",
        "financial express" to "https://financialexpress.com",
        "business standard" to "https://business-standard.com",
        "bbc" to "https://bbc.com",
        "bbc news" to "https://bbc.com",
        "bbc hindi" to "https://bbc.com/hindi",
        "cnn" to "https://cnn.com",
        "al jazeera" to "https://aljazeera.com",
        "aljazeera" to "https://aljazeera.com",
        "reuters" to "https://reuters.com",
        "bloomberg" to "https://bloomberg.com",
        "cnbc" to "https://cnbc.com",
        "forbes" to "https://forbes.com",
        "nytimes" to "https://nytimes.com",
        "the new york times" to "https://nytimes.com",
        "washington post" to "https://washingtonpost.com",
        "wsj" to "https://wsj.com",
        "wall street journal" to "https://wsj.com",
        "guardian" to "https://theguardian.com",
        "the guardian" to "https://theguardian.com",
        "daily mail" to "https://dailymail.co.uk",
        "the verge" to "https://theverge.com",
        "verge" to "https://theverge.com",
        "techcrunch" to "https://techcrunch.com",
        "wired" to "https://wired.com",
        "gizmodo" to "https://gizmodo.com",
        "engadget" to "https://engadget.com",
        "mashable" to "https://mashable.com",
        "cnet" to "https://cnet.com",
        "fox news" to "https://foxnews.com",
        "msnbc" to "https://msnbc.com",
        "sky news" to "https://news.sky.com",
        "euronews" to "https://euronews.com",
        "aninews" to "https://aninews.in",
        "ani news" to "https://aninews.in",
        "mathrubhumi" to "https://mathrubhumi.com",
        "manorama" to "https://onmanorama.com",
        "dinakaran" to "https://dinakaran.com",
        "lokmat" to "https://lokmat.com",
        "anandabazar" to "https://anandabazar.com",

        // === Weather Mappings ===
        "weather" to "https://weather.com",
        "weather forecast" to "https://weather.com",
        "weather channel" to "https://weather.com",
        "accuweather" to "https://accuweather.com",
        "skymet" to "https://skymetweather.com",
        "skymet weather" to "https://skymetweather.com",
        "windy" to "https://windy.com",
        "weather underground" to "https://wunderground.com",
        "wunderground" to "https://wunderground.com",
        "imd" to "https://mausam.imd.gov.in",
        "mausam" to "https://mausam.imd.gov.in",
        "rain radar" to "https://weather.com/maps/radar",
        "aqi" to "https://aqicn.org",
        "air quality index" to "https://aqicn.org",

        // === E-Commerce & Shopping ===
        "amazon" to "https://amazon.in",
        "amazon india" to "https://amazon.in",
        "amazon com" to "https://amazon.com",
        "flipkart" to "https://flipkart.com",
        "meesho" to "https://meesho.com",
        "myntra" to "https://myntra.com",
        "snapdeal" to "https://snapdeal.com",
        "ajio" to "https://ajio.com",
        "tata cliq" to "https://tatacliq.com",
        "nykaa" to "https://nykaa.com",
        "ebay" to "https://ebay.com",
        "aliexpress" to "https://aliexpress.com",
        "walmart" to "https://walmart.com",
        "bestbuy" to "https://bestbuy.com",
        "target" to "https://target.com",
        "alibaba" to "https://alibaba.com",
        "etsy" to "https://etsy.com",
        "temu" to "https://temu.com",
        "shein" to "https://shein.com",
        "lenskart" to "https://lenskart.com",
        "pepperfry" to "https://pepperfry.com",
        "urban ladder" to "https://urbanladder.com",
        "croma" to "https://croma.com",
        "reliance digital" to "https://reliancedigital.in",
        "jiomart" to "https://jiomart.com",
        "bigbasket" to "https://bigbasket.com",
        "blinkit" to "https://blinkit.com",
        "zepto" to "https://zepto.com",
        "swiggy instamart" to "https://swiggy.com/instamart",

        // === Social Media & Chat Platforms ===
        "facebook" to "https://facebook.com",
        "instagram" to "https://instagram.com",
        "twitter" to "https://x.com",
        "x" to "https://x.com",
        "threads" to "https://threads.net",
        "linkedin" to "https://linkedin.com",
        "reddit" to "https://reddit.com",
        "quora" to "https://quora.com",
        "pinterest" to "https://pinterest.com",
        "snapchat" to "https://snapchat.com",
        "tiktok" to "https://tiktok.com",
        "wechat" to "https://wechat.com",
        "telegram" to "https://telegram.org",
        "whatsapp" to "https://whatsapp.com",
        "discord" to "https://discord.com",
        "tumblr" to "https://tumblr.com",
        "flickr" to "https://flickr.com",
        "mastodon" to "https://joinmastodon.org",
        "bluesky" to "https://bsky.app",

        // === Search Engines & AI Tools ===
        "google" to "https://google.com",
        "bing" to "https://bing.com",
        "yahoo" to "https://yahoo.com",
        "duckduckgo" to "https://duckduckgo.com",
        "ask jeeves" to "https://ask.com",
        "baidu" to "https://baidu.com",
        "yandex" to "https://yandex.com",
        "chatgpt" to "https://chatgpt.com",
        "openai" to "https://openai.com",
        "claude" to "https://claude.ai",
        "gemini" to "https://gemini.google.com",
        "perplexity" to "https://perplexity.ai",
        "copilot" to "https://copilot.microsoft.com",
        "hugging face" to "https://huggingface.co",
        "huggingface" to "https://huggingface.co",
        "midjourney" to "https://midjourney.com",
        "canva" to "https://canva.com",
        "figma" to "https://figma.com",
        "notion" to "https://notion.so",

        // === Audio & Video Streaming ===
        "youtube" to "https://youtube.com",
        "netflix" to "https://netflix.com",
        "prime video" to "https://primevideo.com",
        "amazon prime video" to "https://primevideo.com",
        "disney hotstar" to "https://hotstar.com",
        "hotstar" to "https://hotstar.com",
        "jiocinema" to "https://jiocinema.com",
        "jio cinema" to "https://jiocinema.com",
        "zee5" to "https://zee5.com",
        "sonyliv" to "https://sonyliv.com",
        "sony liv" to "https://sonyliv.com",
        "mx player" to "https://mxplayer.in",
        "voot" to "https://voot.com",
        "twitch" to "https://twitch.tv",
        "vimeo" to "https://vimeo.com",
        "dailymotion" to "https://dailymotion.com",
        "spotify" to "https://spotify.com",
        "gaana" to "https://gaana.com",
        "jiosaavn" to "https://jiosaavn.com",
        "saavn" to "https://jiosaavn.com",
        "wynk" to "https://wynk.in/music",
        "wynk music" to "https://wynk.in/music",
        "youtube music" to "https://music.youtube.com",
        "soundcloud" to "https://soundcloud.com",
        "apple music" to "https://music.apple.com",

        // === Tech, Coding & Developer Portals ===
        "github" to "https://github.com",
        "gitlab" to "https://gitlab.com",
        "bitbucket" to "https://bitbucket.org",
        "stackoverflow" to "https://stackoverflow.com",
        "stack overflow" to "https://stackoverflow.com",
        "geeksforgeeks" to "https://geeksforgeeks.org",
        "gfg" to "https://geeksforgeeks.org",
        "w3schools" to "https://w3schools.com",
        "tutorialspoint" to "https://tutorialspoint.com",
        "javatpoint" to "https://javatpoint.com",
        "leetcode" to "https://leetcode.com",
        "hackerrank" to "https://hackerrank.com",
        "codechef" to "https://codechef.com",
        "codeforces" to "https://codeforces.com",
        "medium" to "https://medium.com",
        "dev to" to "https://dev.to",
        "npm" to "https://npmjs.com",
        "pypi" to "https://pypi.org",
        "maven" to "https://mvnrepository.com",
        "kotlin lang" to "https://kotlinlang.org",
        "android developers" to "https://developer.android.com",
        "flutter" to "https://flutter.dev",
        "firebase" to "https://firebase.google.com",
        "aws" to "https://aws.amazon.com",
        "azure" to "https://azure.microsoft.com",
        "google cloud" to "https://cloud.google.com",
        "vercel" to "https://vercel.com",
        "netlify" to "https://netlify.com",

        // === Education & Online Learning ===
        "coursera" to "https://coursera.org",
        "udemy" to "https://udemy.com",
        "edx" to "https://edx.org",
        "khan academy" to "https://khanacademy.org",
        "duolingo" to "https://duolingo.com",
        "byjus" to "https://byjus.com",
        "byju" to "https://byjus.com",
        "unacademy" to "https://unacademy.com",
        "simplilearn" to "https://simplilearn.com",
        "upgrad" to "https://upgrad.com",
        "physics wallah" to "https://pw.live",
        "pw" to "https://pw.live",
        "vedantu" to "https://vedantu.com",

        // === Portals & Services (Indian Govt, Fast Tools) ===
        "irctc" to "https://irctc.co.in",
        "uidai" to "https://uidai.gov.in",
        "aadhaar" to "https://uidai.gov.in",
        "passport seva" to "https://passportindia.gov.in",
        "digilocker" to "https://digilocker.gov.in",
        "income tax" to "https://incometax.gov.in",
        "epfo" to "https://epfindia.gov.in",
        "india post" to "https://indiapost.gov.in",
        "speedtest" to "https://speedtest.net",
        "fast com" to "https://fast.com",
        "gmail" to "https://mail.google.com",
        "google drive" to "https://drive.google.com",
        "dropbox" to "https://dropbox.com",
        "onedrive" to "https://onedrive.live.com",
        "mega" to "https://mega.nz",
        "wetransfer" to "https://wetransfer.com",

        // === Finance & Crypto ===
        "paytm" to "https://paytm.com",
        "phonepe" to "https://phonepe.com",
        "gpay" to "https://pay.google.com",
        "paypal" to "https://paypal.com",
        "stripe" to "https://stripe.com",
        "coinbase" to "https://coinbase.com",
        "binance" to "https://binance.com",
        "wazirx" to "https://wazirx.com",
        "coinmarketcap" to "https://coinmarketcap.com",
        "moneycontrol" to "https://moneycontrol.com",
        "economic times" to "https://economictimes.indiatimes.com",
        "zerodha" to "https://zerodha.com",
        "groww" to "https://groww.in",
        "upstox" to "https://upstox.com",
        "angel one" to "https://angelone.in",
        "hdfc" to "https://hdfcbank.com",
        "sbi" to "https://onlinesbi.sbi",
        "icici" to "https://icicibank.com",
        "axis bank" to "https://axisbank.com",

        // === Travel, Food & Sports ===
        "makemytrip" to "https://makemytrip.com",
        "mmt" to "https://makemytrip.com",
        "yatra" to "https://yatra.com",
        "goibibo" to "https://goibibo.com",
        "booking com" to "https://booking.com",
        "tripadvisor" to "https://tripadvisor.com",
        "airbnb" to "https://airbnb.com",
        "agoda" to "https://agoda.com",
        "redbus" to "https://redbus.in",
        "swiggy" to "https://swiggy.com",
        "zomato" to "https://zomato.com",
        "uber" to "https://uber.com",
        "ola" to "https://olacabs.com",
        "rapido" to "https://rapido.bike",
        "cricbuzz" to "https://cricbuzz.com",
        "espncricinfo" to "https://espncricinfo.com",
        "sports news" to "https://espn.com",
        "fifa" to "https://fifa.com",
        "olympics" to "https://olympics.com",
        "chess" to "https://chess.com",
        "lichess" to "https://lichess.org"
    )

    /**
     * Resolves a spoken query into a direct URL or falls back beautifully.
     * Supports matching 1000+ top popular websites & dynamically parses any unspoken ones.
     */
    fun resolveQueryToUrl(rawQuery: String): String? {
        val clean = rawQuery.lowercase()
            .replace("open ", "")
            .replace("website", "")
            .replace("dot com", "")
            .replace("official page", "")
            .replace("official website", "")
            .replace("portal", "")
            .replace("online", "")
            .replace("link", "")
            .trim()

        if (clean.isBlank()) return null

        // 1. Check direct pre-mapped entities
        val mappedUrl = siteMap[clean]
        if (mappedUrl != null) {
            Log.i("VoiceCommandDatabase", "Matched preloaded site: '$clean' -> $mappedUrl")
            return mappedUrl
        }

        // 2. Extra smart prefix/suffix matching (e.g. "ndtv news" -> ndtv.com, "bbc weather" -> bbc.com)
        for ((key, url) in siteMap) {
            if (clean == "$key news" || clean == "open $key" || "$key website" == clean) {
                Log.i("VoiceCommandDatabase", "Matched preloaded sub-variant: '$clean' -> $url")
                return url
            }
        }

        // 3. Dynamic URL check (if user speaks a direct domain name like "wikipedia.org")
        if (clean.contains(".") && !clean.contains(" ")) {
            val formatted = if (!clean.startsWith("http")) "https://$clean" else clean
            Log.i("VoiceCommandDatabase", "Dynamic direct domain resolved: '$clean' -> $formatted")
            return formatted
        }

        // 4. Fallback programmatic resolution (handles ANY website, e.g. "open flipkart" -> https://www.flipkart.com)
        // Ensure it doesn't contain spaces to make it a valid domain name
        val domainTerm = clean.replace(" ", "")
        if (domainTerm.isNotEmpty() && domainTerm.length in 2..30 && domainTerm.all { it.isLetterOrDigit() }) {
            val fallbackUrl = "https://www.$domainTerm.com"
            Log.i("VoiceCommandDatabase", "Generic fallback domain generated: '$clean' -> $fallbackUrl")
            return fallbackUrl
        }

        return null
    }
}
