package no.nav.helsearbeidsgiver.bucket

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import java.util.UUID

interface BucketStorage {
    fun lagrePdf(
        id: UUID,
        pdf: ByteArray,
    )

    fun hentPdf(id: UUID): ByteArray?
}

class BucketStorageImpl(
    private val bucketName: String,
) : BucketStorage {
    private val storage: Storage = StorageOptions.getDefaultInstance().service

    override fun lagrePdf(
        id: UUID,
        pdf: ByteArray,
    ) {
        val blobInfo =
            BlobInfo
                .newBuilder(BlobId.of(bucketName, id.toString()))
                .setContentType("application/pdf")
                .build()

        storage.create(blobInfo, pdf)
    }

    override fun hentPdf(id: UUID): ByteArray? = storage.get(BlobId.of(bucketName, id.toString()))?.getContent()
}
