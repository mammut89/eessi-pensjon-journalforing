package no.nav.eessi.pensjon.journalforing.services.documentconverter

import no.nav.eessi.pensjon.journalforing.services.eux.MimeType
import org.junit.Test

class somettest {
    val converterService = DocumentConverterService()

    @Test
    fun `Gitt en gyldig jpeg fil når konverterer til pdf så konverter til pdf med jpeg bilde innhold`() {
        val doc = "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7ICqkbgAgF/bbreHZNLpdJrxnq6TU1u3uTEjcE0SFwDwoRS6Dme6BoNB9NbTyamtlLsaebUkUDUSFwDwmfl8HhmlztcW/nxqKwWw+BhASRIXAPAbz8/POajU8dpCp7aA7yVxAQC/Ud9rC53aAr6dxAUA/N7xtYW12EjdqS2gIiQuAOBLer1exJfKhy6ntoDqkLgAgC/Z7/eHN3Ql1QxdTm0BVSNxAQBfVfHQ5dQWUEESFwDwB6oZupzaAipL4gIA/kylQtdmsxmNRk5tAZUlcQEAf6wKoev19fX4/yFxaguoIIkLAPgb3xW68kmtdrsd3/gfTm0B1SRxAQB/6cqh6+eTWlnqTB+KTwKoGIkLAPh7JUJXeszlD9PpND3gYDDodrs/n9RKPaPRaLPZxJcBVJLEBQCc5SR0XYGTWkCNSFwAwLmuE7qc1ALqSOICAC4gha6Xl5fIRudptVrdH1K+Go/H8/l8uVyu1+v4TgC1InEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACUInEBAACU8Z///H9JiJmRbwIcqgAAAABJRU5ErkJggg=="
        val convertModel = DocumentConverterModel(doc, MimeType.JPEG)
        converterService.konverterFraBildeTilBase64EncodedPDF(convertModel)
    }
}