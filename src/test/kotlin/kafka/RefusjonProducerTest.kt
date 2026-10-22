package no.nav.helsearbeidsgiver.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import no.nav.helsearbeidsgiver.vedtak.RefusjonUtfall
import no.nav.helsearbeidsgiver.vedtak.Utfall
import no.nav.helsearbeidsgiver.vedtak.arbeidstakerVedtakMeldingMock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.uuid.toKotlinUuid

class RefusjonProducerTest :
    FunSpec({
        val vedtaksperiodeId = UUID.randomUUID().toKotlinUuid()
        val fnr = Fnr.genererGyldig()
        val orgnr = Orgnr.genererGyldig()
        val refusjonUtfall =
            RefusjonUtfall(
                refusjonUtfallId = UUID.randomUUID().toKotlinUuid(),
                vedtaksperiodeId = vedtaksperiodeId,
                fnr = fnr,
                orgnr = orgnr,
                fom = LocalDate.of(2026, 7, 28),
                tom = LocalDate.of(2026, 8, 3),
                sykepengegrunnlag = 154999.92,
                utfallTilArbeidsgiver = Utfall.INNVILGELSE,
                fattetTidspunkt = LocalDateTime.of(2026, 8, 5, 13, 3, 25),
                journalpostId = "123456789",
                arbeidstakerVedtakMelding = arbeidstakerVedtakMeldingMock(fnr, orgnr),
            )

        test("sender refusjonsutfall til riktig topic med vedtaksperiodeId som key") {
            val mockProducer = mockProducer()

            RefusjonProducer(mockProducer, TEST_TOPIC).send(vedtaksperiodeId, refusjonUtfall)

            val sendt = mockProducer.history()
            sendt shouldHaveSize 1
            sendt.first().topic() shouldBe TEST_TOPIC
            sendt.first().key() shouldBe vedtaksperiodeId.toString()
            sendt
                .first()
                .value()
                .parseJson()
                .fromJson(RefusjonUtfall.serializer()) shouldBe refusjonUtfall
        }

        test("kaster feil når sending feiler") {
            val mockProducer = mockProducer(autoComplete = false)
            mockProducer.sendException = RuntimeException("kafka nede")

            shouldThrow<Exception> {
                RefusjonProducer(mockProducer, TEST_TOPIC).send(vedtaksperiodeId, refusjonUtfall)
            }
            mockProducer.history().shouldBeEmpty()
        }
    })
