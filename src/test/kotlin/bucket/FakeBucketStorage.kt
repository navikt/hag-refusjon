package no.nav.helsearbeidsgiver.bucket

import java.util.UUID

class FakeBucketStorage : BucketStorage {
    val pdfer = HashMap<UUID, ByteArray>()

    override fun lagrePdf(
        id: UUID,
        pdf: ByteArray,
    ) {
        pdfer[id] = pdf
    }

    override fun hentPdf(id: UUID): ByteArray? = pdfer[id]
}
