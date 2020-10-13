package no.nav.eessi.pensjon.oppgaverouting

enum class Bosatt {
    NORGE,
    UTLAND,
    UKJENT;

    companion object {
        fun fraLandkode(landkode: String?) =
                when {
                    landkode.isNullOrEmpty() -> UKJENT
                    landkode == "NOR" -> NORGE
                    else -> UTLAND
                }
    }
}

enum class Enhet(val enhetsNr: String) {
    PENSJON_UTLAND("0001"),
    UFORE_UTLANDSTILSNITT("4476"),
    UFORE_UTLAND("4475"),
    NFP_UTLAND_AALESUND("4862"),
    NFP_UTLAND_OSLO("4803"),
    ID_OG_FORDELING("4303"),
    DISKRESJONSKODE("2103"),
    OKONOMI_PENSJON("4819"),
    AUTOMATISK_JOURNALFORING("9999");

    companion object {
        fun getEnhet(enhetsNr: String): Enhet? = values().find { it.enhetsNr == enhetsNr }
    }
}
