package no.nav.helsearbeidsgiver.bucket

import kotlin.uuid.Uuid

class FakeBucketStorage : BucketStorage {
    val pdfer = HashMap<Uuid, ByteArray>()

    override fun lagrePdf(
        id: Uuid,
        pdf: ByteArray,
    ) {
        pdfer[id] = pdf
    }

    override fun hentPdf(id: Uuid): ByteArray? = pdfer[id]
}
