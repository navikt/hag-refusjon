package no.nav.helsearbeidsgiver.refusjon

import no.nav.helsearbeidsgiver.arkiv.ArkivService
import no.nav.helsearbeidsgiver.bucket.BucketStorage
import no.nav.helsearbeidsgiver.kafka.RefusjonProducer
import no.nav.helsearbeidsgiver.person.PdlService
import no.nav.helsearbeidsgiver.refusjonskrav.RefusjonskravService
import no.nav.helsearbeidsgiver.utils.genererRefusjonPdf
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.virksomhet.VirksomhetsnavnKlient
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

class VedtakService(
    private val bucketStorage: BucketStorage,
    private val refusjonProducer: RefusjonProducer,
    private val virksomhetsnavnKlient: VirksomhetsnavnKlient,
    private val pdlService: PdlService,
    private val arkivService: ArkivService,
    private val refusjonskravService: RefusjonskravService,
    private val refusjonUtfallRepository: RefusjonUtfallRepository,
) {
    private val logger = LoggerFactory.getLogger(VedtakService::class.java)
    private val sikkerLogger = sikkerLogger()

    suspend fun behandle(melding: ArbeidstakerVedtakMelding): Uuid {
        sikkerLogger.info(
            "Behandler arbeidstakervedtak med behandlingId ${melding.behandlingId} og vedtaksperiodeId ${melding.vedtaksperiodeId}:\n" +
                melding.toJson(ArbeidstakerVedtakMelding.serializer()).toPretty(),
        )

        val refusjonUtfall =
            refusjonUtfallRepository.hent(melding.behandlingId)
                ?: hentDataOgLagreRefusjonUtfall(melding)
        val refusjonUtfallId = refusjonUtfall.refusjonUtfallId

        val pdf =
            try {
                bucketStorage.hentPdf(refusjonUtfallId)?.also {
                    sikkerLogger.info("Fant eksisterende PDF for refusjonsutfall med refusjonsutfallId $refusjonUtfallId.")
                } ?: genererRefusjonPdf(refusjonUtfall.tilRefusjonUtfallPdfData())
                    .also {
                        bucketStorage.lagrePdf(refusjonUtfallId, it)
                        sikkerLogger.info(
                            "Genererte og lagret PDF for refusjonsutfall med refusjonsutfallId $refusjonUtfallId og vedtaksperiodeId ${melding.vedtaksperiodeId}.",
                        )
                    }
            } catch (e: Exception) {
                throw loggOgKast(
                    "Feil ved generering eller lagring av PDF for refusjonsutfall med refusjonUtfallId $refusjonUtfallId og vedtaksperiodeId ${melding.vedtaksperiodeId}.",
                    e,
                )
            }

        val journalpostId =
            try {
                arkivService.arkiver(refusjonUtfall, pdf)
            } catch (e: Exception) {
                throw loggOgKast(
                    "Feil ved arkivering av refusjonsutfall med refusjonsutfallId $refusjonUtfallId og vedtaksperiodeId ${melding.vedtaksperiodeId}.",
                    e,
                )
            }

        sikkerLogger.info(
            "Arkiverte refusjonsutfall med refusjonsutfallId $refusjonUtfallId og vedtaksperiodeId ${melding.vedtaksperiodeId} i journalpost $journalpostId.",
        )

        try {
            refusjonProducer.send(melding.vedtaksperiodeId, refusjonUtfall)
        } catch (e: Exception) {
            throw loggOgKast(
                "Feil ved publisering av refusjonsutfall med refusjonsutfallId $refusjonUtfallId og vedtaksperiodeId ${melding.vedtaksperiodeId}.",
                e,
            )
        }

        sikkerLogger.info(
            "Publiserte refusjonsutfall med refusjonsutfallId $refusjonUtfallId og vedtaksperiodeId ${melding.vedtaksperiodeId}.",
        )

        return refusjonUtfallId
    }

    private suspend fun hentDataOgLagreRefusjonUtfall(melding: ArbeidstakerVedtakMelding): RefusjonUtfall {
        val refusjonskrav = refusjonskravService.hentRefusjonskrav(inntektsmeldingIder = melding.hagInntektsmeldingReferanser())

        val arbeidsgiverNavn =
            try {
                virksomhetsnavnKlient.hentVirksomhetsnavn(melding.organisasjonsnummer)
                    ?: throw IllegalStateException("Fant ikke virksomhetsnavn.")
            } catch (e: Exception) {
                throw loggOgKast(
                    "Feil ved henting av virksomhetsnavn for refusjonsutfall med behandlingId ${melding.behandlingId} og vedtaksperiodeId ${melding.vedtaksperiodeId}.",
                    e,
                )
            }

        val sykmeldtNavn =
            try {
                pdlService.hentSykmeldtnavn(melding.foedselsnummer)
                    ?: throw IllegalStateException("Fant ikke sykmeldtnavn.")
            } catch (e: Exception) {
                throw loggOgKast(
                    "Feil ved henting av sykmeldtnavn for refusjonsutfall med behandlingId ${melding.behandlingId} og vedtaksperiodeId ${melding.vedtaksperiodeId}.",
                    e,
                )
            }

        val refusjonUtfall =
            melding.tilRefusjonUtfall(UUID.randomUUID().toKotlinUuid(), refusjonskrav, arbeidsgiverNavn, sykmeldtNavn)

        try {
            val lagret = refusjonUtfallRepository.lagre(melding.behandlingId, refusjonUtfall)
            if (lagret) return refusjonUtfall

            // Mot race condition: Bruk alltid det lagrede refusjonsutfallet slik at id-en er den samme i PDF, arkiv og Kafka.
            return checkNotNull(refusjonUtfallRepository.hent(melding.behandlingId)) {
                "Fant ikke lagret refusjonsutfall etter mislykket lagring."
            }
        } catch (e: Exception) {
            throw loggOgKast(
                "Feil ved lagring av refusjonsutfall med behandlingId ${melding.behandlingId} og vedtaksperiodeId ${melding.vedtaksperiodeId}.",
                e,
            )
        }
    }

    private fun loggOgKast(
        melding: String,
        feil: Exception,
    ): Exception {
        logger.error(melding)
        sikkerLogger.error(melding, feil)
        return IllegalStateException(melding, feil)
    }
}
