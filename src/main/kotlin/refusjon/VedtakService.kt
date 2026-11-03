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
) {
    private val logger = LoggerFactory.getLogger(VedtakService::class.java)
    private val sikkerLogger = sikkerLogger()

    suspend fun behandle(melding: ArbeidstakerVedtakMelding): Uuid {
        sikkerLogger.info(
            "Behandler arbeidstakervedtak med behandlingId ${melding.behandlingId} og vedtaksperiodeId ${melding.vedtaksperiodeId}:\n" +
                melding.toJson(ArbeidstakerVedtakMelding.serializer()).toPretty(),
        )

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

        val refusjonskrav = refusjonskravService.hentRefusjonskrav(inntektsmeldingIder = melding.hagInntektsmeldingReferanser())
        val refusjonUtfallId = UUID.randomUUID().toKotlinUuid()
        val refusjonUtfall = melding.tilRefusjonUtfall(refusjonUtfallId, refusjonskrav, arbeidsgiverNavn, sykmeldtNavn)

        val pdf =
            try {
                val generertPdf = genererRefusjonPdf(refusjonUtfall.tilRefusjonUtfallPdfData())
                bucketStorage.lagrePdf(refusjonUtfallId, generertPdf)
                generertPdf
            } catch (e: Exception) {
                throw loggOgKast(
                    "Feil ved generering eller lagring av PDF for refusjonsutfall med refusjonUtfallId $refusjonUtfallId og vedtaksperiodeId ${melding.vedtaksperiodeId}.",
                    e,
                )
            }

        sikkerLogger.info(
            "Lagret PDF for refusjonsutfall med refusjonsutfallId $refusjonUtfallId og vedtaksperiodeId ${melding.vedtaksperiodeId}.",
        )

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

    private fun loggOgKast(
        melding: String,
        feil: Exception,
    ): Exception {
        logger.error(melding)
        sikkerLogger.error(melding, feil)
        return IllegalStateException(melding, feil)
    }
}
