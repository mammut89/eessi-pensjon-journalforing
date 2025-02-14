package no.nav.eessi.pensjon.pdf

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class SedDokumenter(
        val sed: EuxDokument,
        val vedlegg: List<EuxDokument>?
){
    override fun toString(): String {
        val mapper = jacksonObjectMapper()
        return mapper.writeValueAsString(this)
    }
}

class EuxDokument(val filnavn: String?,
                       val mimeType: MimeType?,
                       val innhold: String)

enum class MimeType  : Code {
    @JsonProperty("application/pdf")
    PDF {
        override fun toString() = "application/pdf"
        override fun decode() = "PDF"
    },
    PDFA {
        override fun toString() = "application/pdfa"
        override fun decode() = "PDFA"
    },
    @JsonProperty("image/jpg")
    JPG {
        override fun toString() = "image/jpg"
        override fun decode() = "JPG"
    },
    @JsonProperty("image/jpeg")
    JPEG {
        override fun toString() = "image/jpeg"
        override fun decode() = "JPEG"
    },
    @JsonProperty("image/tiff")
    TIFF {
        override fun toString() = "image/tiff"
        override fun decode() = "TIFF"
    },
    @JsonProperty("image/tif")
    TIF {
        override fun toString() = "image/tif"
        override fun decode() = "TIF"
    },
    @JsonProperty("image/png")
    PNG {
        override fun toString() = "image/png"
        override fun decode() = "PNG"
    }
}

class JournalPostDokument(
        val brevkode: String? = null,
        val dokumentKategori: String? = "SED",
        val dokumentvarianter: List<Dokumentvarianter>, //REQUIRED
        val tittel: String? = null
)

class Dokumentvarianter(
    val filtype: String, //REQUIRED
    val fysiskDokument: String, //REQUIRED
    val variantformat: Variantformat //REQUIRED
)

enum class Variantformat {
    ARKIV
}

interface Code {
    fun decode(): String
}
