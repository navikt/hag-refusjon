package no.nav.helsearbeidsgiver.bucket

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import kotlin.uuid.Uuid

interface BucketStorage {
    fun lagrePdf(
        id: Uuid,
        pdf: ByteArray,
    )

    fun hentPdf(id: Uuid): ByteArray?
}

class BucketStorageImpl(
    private val bucketName: String,
) : BucketStorage {
    private val storage: Storage = StorageOptions.getDefaultInstance().service

    override fun lagrePdf(
        id: Uuid,
        pdf: ByteArray,
    ) {
        val blobInfo =
            BlobInfo
                .newBuilder(BlobId.of(bucketName, id.toString()))
                .setContentType("application/pdf")
                .build()

        storage.create(blobInfo, pdf)
    }

    override fun hentPdf(id: Uuid): ByteArray? = storage.get(BlobId.of(bucketName, id.toString()))?.getContent()
}
