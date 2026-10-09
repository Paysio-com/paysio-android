package com.paysio.sdk.ui

/**
 * Country and US-state data for the billing address pickers, copied from the RN SDK's
 * `src/ui/countries.ts` and `src/ui/usStates.ts` (same order, same names). The popular
 * countries lead in the same order as the web's CountrySelect, the rest are
 * alphabetical. Flags are regional indicator pairs, no artwork needed.
 */
object PaysioCountries {
    data class Country(val code: String, val name: String) {
        val flag: String get() = flagEmoji(code)
    }

    data class UsState(val code: String, val name: String)

    /** Regional-indicator flag for an ISO 3166-1 alpha-2 code, "" for anything else. */
    fun flagEmoji(code: String): String {
        val cc = code.uppercase()
        if (cc.length != 2 || !cc.all { it in 'A'..'Z' }) return ""
        return cc.map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")
    }

    private val RAW: List<Pair<String, String>> = listOf(
        "US" to "United States", "CA" to "Canada", "GB" to "United Kingdom", "AU" to "Australia", "NZ" to "New Zealand",
        "IE" to "Ireland", "DE" to "Germany", "FR" to "France", "ES" to "Spain", "IT" to "Italy", "NL" to "Netherlands",
        "AF" to "Afghanistan", "AX" to "Åland Islands", "AL" to "Albania", "DZ" to "Algeria", "AS" to "American Samoa",
        "AD" to "Andorra", "AO" to "Angola", "AI" to "Anguilla", "AQ" to "Antarctica", "AG" to "Antigua & Barbuda",
        "AR" to "Argentina", "AM" to "Armenia", "AW" to "Aruba", "AT" to "Austria", "AZ" to "Azerbaijan", "BS" to "Bahamas",
        "BH" to "Bahrain", "BD" to "Bangladesh", "BB" to "Barbados", "BY" to "Belarus", "BE" to "Belgium", "BZ" to "Belize",
        "BJ" to "Benin", "BM" to "Bermuda", "BT" to "Bhutan", "BO" to "Bolivia", "BA" to "Bosnia & Herzegovina",
        "BW" to "Botswana", "BV" to "Bouvet Island", "BR" to "Brazil", "IO" to "British Indian Ocean Territory",
        "VG" to "British Virgin Islands", "BN" to "Brunei", "BG" to "Bulgaria", "BF" to "Burkina Faso", "BI" to "Burundi",
        "KH" to "Cambodia", "CM" to "Cameroon", "CV" to "Cape Verde", "BQ" to "Caribbean Netherlands", "KY" to "Cayman Islands",
        "CF" to "Central African Republic", "TD" to "Chad", "CL" to "Chile", "CN" to "China", "CX" to "Christmas Island",
        "CC" to "Cocos (Keeling) Islands", "CO" to "Colombia", "KM" to "Comoros", "CG" to "Congo - Brazzaville",
        "CD" to "Congo - Kinshasa", "CK" to "Cook Islands", "CR" to "Costa Rica", "CI" to "Côte d’Ivoire", "HR" to "Croatia",
        "CU" to "Cuba", "CW" to "Curaçao", "CY" to "Cyprus", "CZ" to "Czechia", "DK" to "Denmark", "DJ" to "Djibouti",
        "DM" to "Dominica", "DO" to "Dominican Republic", "EC" to "Ecuador", "EG" to "Egypt", "SV" to "El Salvador",
        "GQ" to "Equatorial Guinea", "ER" to "Eritrea", "EE" to "Estonia", "SZ" to "Eswatini", "ET" to "Ethiopia",
        "FK" to "Falkland Islands", "FO" to "Faroe Islands", "FJ" to "Fiji", "FI" to "Finland", "GF" to "French Guiana",
        "PF" to "French Polynesia", "TF" to "French Southern Territories", "GA" to "Gabon", "GM" to "Gambia", "GE" to "Georgia",
        "GH" to "Ghana", "GI" to "Gibraltar", "GR" to "Greece", "GL" to "Greenland", "GD" to "Grenada", "GP" to "Guadeloupe",
        "GU" to "Guam", "GT" to "Guatemala", "GG" to "Guernsey", "GN" to "Guinea", "GW" to "Guinea-Bissau", "GY" to "Guyana",
        "HT" to "Haiti", "HM" to "Heard & McDonald Islands", "HN" to "Honduras", "HK" to "Hong Kong SAR China",
        "HU" to "Hungary", "IS" to "Iceland", "IN" to "India", "ID" to "Indonesia", "IR" to "Iran", "IQ" to "Iraq",
        "IM" to "Isle of Man", "IL" to "Israel", "JM" to "Jamaica", "JP" to "Japan", "JE" to "Jersey", "JO" to "Jordan",
        "KZ" to "Kazakhstan", "KE" to "Kenya", "KI" to "Kiribati", "KW" to "Kuwait", "KG" to "Kyrgyzstan", "LA" to "Laos",
        "LV" to "Latvia", "LB" to "Lebanon", "LS" to "Lesotho", "LR" to "Liberia", "LY" to "Libya", "LI" to "Liechtenstein",
        "LT" to "Lithuania", "LU" to "Luxembourg", "MO" to "Macao SAR China", "MG" to "Madagascar", "MW" to "Malawi",
        "MY" to "Malaysia", "MV" to "Maldives", "ML" to "Mali", "MT" to "Malta", "MH" to "Marshall Islands", "MQ" to "Martinique",
        "MR" to "Mauritania", "MU" to "Mauritius", "YT" to "Mayotte", "MX" to "Mexico", "FM" to "Micronesia", "MD" to "Moldova",
        "MC" to "Monaco", "MN" to "Mongolia", "ME" to "Montenegro", "MS" to "Montserrat", "MA" to "Morocco", "MZ" to "Mozambique",
        "MM" to "Myanmar (Burma)", "NA" to "Namibia", "NR" to "Nauru", "NP" to "Nepal", "NC" to "New Caledonia",
        "NI" to "Nicaragua", "NE" to "Niger", "NG" to "Nigeria", "NU" to "Niue", "NF" to "Norfolk Island", "KP" to "North Korea",
        "MK" to "North Macedonia", "MP" to "Northern Mariana Islands", "NO" to "Norway", "OM" to "Oman", "PK" to "Pakistan",
        "PW" to "Palau", "PS" to "Palestinian Territories", "PA" to "Panama", "PG" to "Papua New Guinea", "PY" to "Paraguay",
        "PE" to "Peru", "PH" to "Philippines", "PN" to "Pitcairn Islands", "PL" to "Poland", "PT" to "Portugal",
        "PR" to "Puerto Rico", "QA" to "Qatar", "RE" to "Réunion", "RO" to "Romania", "RU" to "Russia", "RW" to "Rwanda",
        "WS" to "Samoa", "SM" to "San Marino", "ST" to "São Tomé & Príncipe", "SA" to "Saudi Arabia", "SN" to "Senegal",
        "RS" to "Serbia", "SC" to "Seychelles", "SL" to "Sierra Leone", "SG" to "Singapore", "SX" to "Sint Maarten",
        "SK" to "Slovakia", "SI" to "Slovenia", "SB" to "Solomon Islands", "SO" to "Somalia", "ZA" to "South Africa",
        "GS" to "South Georgia & South Sandwich Islands", "KR" to "South Korea", "SS" to "South Sudan", "LK" to "Sri Lanka",
        "BL" to "St. Barthélemy", "SH" to "St. Helena", "KN" to "St. Kitts & Nevis", "LC" to "St. Lucia", "MF" to "St. Martin",
        "PM" to "St. Pierre & Miquelon", "VC" to "St. Vincent & Grenadines", "SD" to "Sudan", "SR" to "Suriname",
        "SJ" to "Svalbard & Jan Mayen", "SE" to "Sweden", "CH" to "Switzerland", "SY" to "Syria", "TW" to "Taiwan",
        "TJ" to "Tajikistan", "TZ" to "Tanzania", "TH" to "Thailand", "TL" to "Timor-Leste", "TG" to "Togo", "TK" to "Tokelau",
        "TO" to "Tonga", "TT" to "Trinidad & Tobago", "TN" to "Tunisia", "TR" to "Türkiye", "TM" to "Turkmenistan",
        "TC" to "Turks & Caicos Islands", "TV" to "Tuvalu", "UM" to "U.S. Outlying Islands", "VI" to "U.S. Virgin Islands",
        "UG" to "Uganda", "UA" to "Ukraine", "AE" to "United Arab Emirates", "UY" to "Uruguay", "UZ" to "Uzbekistan",
        "VU" to "Vanuatu", "VA" to "Vatican City", "VE" to "Venezuela", "VN" to "Vietnam", "WF" to "Wallis & Futuna",
        "EH" to "Western Sahara", "YE" to "Yemen", "ZM" to "Zambia", "ZW" to "Zimbabwe",
    )

    val list: List<Country> = RAW.map { (code, name) -> Country(code, name) }

    /** The country's English name, the code itself when unknown, "" for blank. */
    fun countryName(code: String?): String {
        val cc = code?.trim()?.uppercase() ?: return ""
        if (cc.isEmpty()) return ""
        return list.firstOrNull { it.code == cc }?.name ?: cc
    }

    /** USPS codes the state picker offers: states, DC, territories, military. */
    val usStates: List<UsState> = listOf(
        UsState("AL", "Alabama"), UsState("AK", "Alaska"), UsState("AZ", "Arizona"),
        UsState("AR", "Arkansas"), UsState("CA", "California"), UsState("CO", "Colorado"),
        UsState("CT", "Connecticut"), UsState("DE", "Delaware"), UsState("DC", "District of Columbia"),
        UsState("FL", "Florida"), UsState("GA", "Georgia"), UsState("HI", "Hawaii"),
        UsState("ID", "Idaho"), UsState("IL", "Illinois"), UsState("IN", "Indiana"),
        UsState("IA", "Iowa"), UsState("KS", "Kansas"), UsState("KY", "Kentucky"),
        UsState("LA", "Louisiana"), UsState("ME", "Maine"), UsState("MD", "Maryland"),
        UsState("MA", "Massachusetts"), UsState("MI", "Michigan"), UsState("MN", "Minnesota"),
        UsState("MS", "Mississippi"), UsState("MO", "Missouri"), UsState("MT", "Montana"),
        UsState("NE", "Nebraska"), UsState("NV", "Nevada"), UsState("NH", "New Hampshire"),
        UsState("NJ", "New Jersey"), UsState("NM", "New Mexico"), UsState("NY", "New York"),
        UsState("NC", "North Carolina"), UsState("ND", "North Dakota"), UsState("OH", "Ohio"),
        UsState("OK", "Oklahoma"), UsState("OR", "Oregon"), UsState("PA", "Pennsylvania"),
        UsState("RI", "Rhode Island"), UsState("SC", "South Carolina"), UsState("SD", "South Dakota"),
        UsState("TN", "Tennessee"), UsState("TX", "Texas"), UsState("UT", "Utah"),
        UsState("VT", "Vermont"), UsState("VA", "Virginia"), UsState("WA", "Washington"),
        UsState("WV", "West Virginia"), UsState("WI", "Wisconsin"), UsState("WY", "Wyoming"),
        UsState("AS", "American Samoa"), UsState("GU", "Guam"), UsState("MP", "Northern Mariana Islands"),
        UsState("PR", "Puerto Rico"), UsState("VI", "U.S. Virgin Islands"),
        UsState("AA", "Armed Forces Americas"), UsState("AE", "Armed Forces Europe"), UsState("AP", "Armed Forces Pacific"),
    )

    /** A US state code for a code or full name ("ny", "New York" -> "NY"); null if unknown. */
    fun toUsStateCode(raw: String?): String? {
        val v = raw?.trim() ?: return null
        if (v.isEmpty()) return null
        val upper = v.uppercase()
        usStates.firstOrNull { it.code == upper }?.let { return it.code }
        val lower = v.lowercase()
        return usStates.firstOrNull { it.name.lowercase() == lower }?.code
    }
}
