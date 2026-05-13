package com.fmz.spenitaicore.util

/**
 * Ported from Spenit's CategorySuggestionService.
 * Suggests spending and income categories based on merchant/source keywords.
 * Falls back to the default ("General" / "Other Income") when no match is found.
 */
object CategorySuggestionService {

    fun suggestExpenseCategory(merchantOrDescription: String?): String {
        if (merchantOrDescription.isNullOrBlank()) return "General"
        val text = merchantOrDescription.lowercase()

        // ── Transport ───────────────────────────────────────────
        if (containsAny(text,
                "paydirect", "rfid", "toll", "toll plaza", "highway toll", "expressway",
                "lebuhraya", "smart tag", "plus highway", "litrak", "nse toll",
                "north south expressway", "tol", "lpt", "nkve", "sprint"
            )) return "Toll"
        if (containsAny(text,
                "parking", "car park", "parking lot", "parkir", "wilson parking",
                "secure parking", "exchange trx", "pavilion parking"
            )) return "Parking"
        if (containsAny(text,
                "petronas", "shell", "petron", "caltex", "bhp", "esso", "mobil",
                "fuel", "petrol", "diesel", "gas station", "ev charging", "ev charger",
                "charge point", "chargev", "tesla supercharger", "jomcharge", "gentari",
                "cdm", "handal"
            )) return "Fuel/EV Charging"
        if (containsAny(text,
                "grab ride", "grab car", "e-hailing", "ride", "uber", "taxi",
                "ezlink", "transit", "rapidkl", "mrt", "lrt", "ktm", "bus", "train",
                "subway", "metro", "public transport", "touch n go", "tng ewallet",
                "transport"
            )) return "Transport"

        // ── Food & Groceries ────────────────────────────────────
        if (containsAny(text,
                "aeon", "mercato", "grocery", "grocer", "kk mart", "mart",
                "supermarket", "walmart", "kroger", "safeway", "tesco", "giant",
                "coles", "albertsons", "lidl", "aldi", "jaya grocer", "village grocer",
                "lotus", "mydin", "99 speedmart", "speedmart", "7-eleven", "familymart",
                "hypermarket"
            )) return "Groceries"
        if (containsAny(text,
                "restaurant", "yakitori", "churros", "ramen", "leleh", "lausanjee",
                "naknak", "herbina", "verrona", "luckin", "moknab", "eat", "mok nab",
                "food", "grab ec", "beriani", "grabpay-ec", "planet", "pasta", "chef",
                "restoran", "resto", "makan", "nasi", "kopitiam", "kfc", "mcdonald",
                "mcd", "starbucks", "coffee bean", "zus", "tealive", "pizza", "burger",
                "cafe", "sushi", "bakery", "bistro", "foodpanda", "grabfood",
                "food delivery", "deliveroo", "dining", "santap", "rice", "noodle",
                "coffee", "kopi", "milo"
            )) return "Food & Drinks"

        // ── Shopping & Gadgets ──────────────────────────────────
        if (containsAny(text,
                "apple store", "app store", "google play", "playstation", "xbox",
                "nintendo", "switch", "steam", "laptop", "phone", "smartphone",
                "iphone", "ipad", "macbook", "samsung", "xiaomi", "oppo", "vivo",
                "huawei", "computer", "electronics", "gadget", "camera", "earbuds",
                "headphone", "charger"
            )) return "Gadget"
        if (containsAny(text,
                "amazon", "ebay", "shopee", "lazada", "zalora", "taobao", "aliexpress",
                "etsy", "best buy", "ikea", "mr diy", "daiso", "h&m", "uniqlo",
                "zara", "adidas", "nike", "fashion", "clothing", "apparel", "footwear",
                "shoe", "department store", "retail", "mall"
            )) return "Shopping"

        // ── Subscriptions & Entertainment ───────────────────────
        if (containsAny(text,
                "netflix", "apple.com/bill", "spotify", "hulu", "disney+", "disney plus",
                "hbo", "apple music", "amazon prime", "youtube premium", "icloud",
                "subscription", "streaming", "claude", "gemini", "chatgpt", "openai",
                "notion", "figma", "canva", "github", "microsoft 365", "office 365"
            )) return "Subscriptions"
        if (containsAny(text,
                "movie", "cinema", "gsc", "tgv", "concert", "ticketmaster",
                "eventbrite", "karaoke", "theme park", "bowling", "entertainment"
            )) return "Entertainment"

        // ── Health ──────────────────────────────────────────────
        if (containsAny(text,
                "hospital", "pharmacy", "doctor", "medical", "dental", "dentist",
                "clinic", "healthcare", "medicine", "guardian", "caring pharmacy",
                "healthland", "watsons", "klinik", "optical", "optometrist", "physio",
                "therapy"
            )) return "Health & Medical"

        // ── Bills & Utilities ───────────────────────────────────
        if (containsAny(text,
                "electric", "tenaga nasional", "electricity", "indah water", "water",
                "utility", "phone bill", "mobile bill", "telco", "tnb", "sesb",
                "maxis", "digi", "umobile", "u mobile", "hotlink", "celcom",
                "celcomdigi", "unifi", "time internet", "astro", "broadband",
                "internet bill"
            )) return "Utilities"

        // ── Travel ──────────────────────────────────────────────
        if (containsAny(text,
                "airasia", "malaysia airlines", "batik air", "firefly", "flight",
                "airline", "airport", "hotel", "airbnb", "booking.com", "expedia",
                "tripadvisor", "agoda", "traveloka", "kayak", "trivago", "holiday",
                "resort", "hostel", "motel", "travel", "tour"
            )) return "Travel"

        // ── Education ───────────────────────────────────────────
        if (containsAny(text,
                "tuition", "course", "school", "bookstore", "textbook", "education",
                "university", "college", "academy", "edx", "coursera", "udemy",
                "skillshare", "training", "workshop", "seminar"
            )) return "Education"

        // ── Personal Care ───────────────────────────────────────
        if (containsAny(text,
                "hair", "barber", "beauty", "spa", "salon", "cosmetics", "skincare",
                "personal care", "sephora", "makeup", "nail"
            )) return "Personal Care"

        // ── Financial ───────────────────────────────────────────
        if (containsAny(text,
                "insurance", "premium", "policy", "aia", "prudential", "great eastern",
                "allianz", "etiqa", "takaful", "zurich"
            )) return "Insurance"
        if (containsAny(text, "loan repayment", "loan payment", "personal loan",
                "hire purchase", "auto loan", "student loan", "ptptn"
            )) return "Loan"
        if (containsAny(text, "mortgage", "home loan", "housing loan", "property loan"
            )) return "Mortgage"
        if (containsAny(text, "credit card", "card payment", "visa payment",
                "mastercard payment", "amex", "statement payment"
            )) return "Credit Card"
        if (containsAny(text, "rent", "rental", "landlord", "tenant", "room rent",
                "house rent", "apartment rent", "condo rent"
            )) return "Rental"
        if (containsAny(text, "stock", "broker", "brokerage", "etf", "unit trust",
                "mutual fund", "crypto", "bitcoin", "investment", "stashaway",
                "versa", "asnb", "maybank investment"
            )) return "Investment"

        // ── Business & Services ─────────────────────────────────
        if (containsAny(text, "office supplies", "software license", "business",
                "company", "ssm", "lhdn", "tax filing", "accounting", "invoice",
                "client expense"
            )) return "Business"
        if (containsAny(text, "hardware", "furniture", "home repair", "renovation",
                "plumbing", "electrician", "gardening", "nursery", "home garden",
                "paint", "tiles", "appliance"
            )) return "Home & Garden"
        if (containsAny(text, "car wash", "sohrab", "faysal", "cleaning", "laundry",
                "repair", "maintenance", "service fee", "service charge",
                "professional service", "legal fee", "consultation fee"
            )) return "Services"
        if (containsAny(text, "transfer", "fund transfer", "duitnow", "maybank2u",
                "public bank", "pb engage", "hong leong bank", "hlb connect",
                "cimb clicks", "rhb now", "interbank", "instant transfer",
                "money transfer", "send money", "send funds"
            )) return "General"  // transfers are ambiguous — stay generic

        return "General"
    }

    fun suggestIncomeCategory(source: String?, notesOrDescription: String? = null): String {
        val text = "${source.orEmpty()} ${notesOrDescription.orEmpty()}".lowercase()

        if (containsAny(text,
                "salary", "payroll", "wages", "pay slip", "payslip", "monthly pay",
                "basic pay", "net pay", "employer"
            )) return "Salary"
        if (containsAny(text,
                "freelance", "contract", "gig", "consulting", "consultant",
                "side job", "part time", "project fee"
            )) return "Freelance"
        if (containsAny(text,
                "business", "client payment", "invoice", "sales", "sale", "sell",
                "seller", "shop", "customer payment", "merchant settlement"
            )) return "Business"
        if (containsAny(text,
                "dividend"
            )) return "Dividend"
        if (containsAny(text,
                "royalty", "royalties", "licensing"
            )) return "Royalty"
        if (containsAny(text,
                "interest", "investment", "stock", "bond", "brokerage",
                "unit trust", "mutual fund", "capital gain", "yield", "crypto"
            )) return "Investment"
        if (containsAny(text,
                "rent", "rental", "tenant", "airbnb", "homestay"
            )) return "Rental"
        if (containsAny(text,
                "commission", "commision", "referral fee", "sales commission"
            )) return "Commision"
        if (containsAny(text,
                "bonus", "incentive", "performance reward"
            )) return "Bonus"
        if (containsAny(text,
                "gift", "allowance", "angpau", "ang pow", "duit raya"
            )) return "Gift"
        if (containsAny(text,
                "refund", "cashback", "rebate", "reimbursement", "claim",
                "returned payment"
            )) return "Refund"
        if (containsAny(text,
                "transfer", "duitnow", "fund transfer", "interbank",
                "instant transfer", "received from", "money received",
                "payment received"
            )) return "Transfer"

        return "Other Income"
    }

    /**
     * Returns true if the AI category is a generic fallback that local
     * keyword matching should try to improve upon.
     */
    fun isGenericCategory(category: String?, isIncome: Boolean): Boolean {
        if (category.isNullOrBlank()) return true
        val trimmed = category.trim()
        return if (isIncome) trimmed == "Other Income" || trimmed == "Salary"
        else trimmed == "General"
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean =
        keywords.any { text.contains(it) }
}
