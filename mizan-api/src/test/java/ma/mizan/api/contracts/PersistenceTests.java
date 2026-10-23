package ma.mizan.api.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Flyway V1 against real PostgreSQL 18: Hibernate validates the mapping (ddl-auto=validate), and
 * the database itself enforces the rules the DB doc puts in CHECK constraints and cascades.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersistenceTests {

  @Autowired ContractRepository contracts;
  @Autowired AnalysisRepository analyses;
  @Autowired EntityManager em;
  @Autowired JdbcClient jdbc;

  @Test
  void savesAContractWithAPendingAnalysisUnderUuidV7Ids() {
    Contract contract = contracts.save(new Contract("sub-a", "bail.pdf", 3, "Article 1 …"));
    Analysis analysis = analyses.save(new Analysis(contract, Language.ARY));
    em.flush();
    em.clear();

    assertThat(contract.id().version()).isEqualTo(7);
    assertThat(analysis.id().version()).isEqualTo(7);
    assertThat(contracts.findByIdAndOwnerSub(contract.id(), "sub-a"))
        .hasValueSatisfying(
            c -> {
              assertThat(c.fileName()).isEqualTo("bail.pdf");
              assertThat(c.textContent()).isEqualTo("Article 1 …");
              assertThat(c.createdAt()).isEqualTo(contract.createdAt());
            });
    assertThat(analyses.findByContractIdOrderByRequestedAtDesc(contract.id()))
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.status()).isEqualTo(AnalysisStatus.PENDING);
              assertThat(a.language()).isEqualTo(Language.ARY);
            });
    // Tests share one database: always scope raw queries to this test's rows.
    assertThat(
            jdbc.sql("SELECT language FROM mizan.analyses WHERE id = ?")
                .param(analysis.id())
                .query(String.class)
                .single())
        .isEqualTo("ary");
  }

  @Test
  void anotherOwnerCannotFindTheContract() {
    Contract contract = contracts.save(new Contract("sub-a", "a.pdf", 1, "text"));
    em.flush();
    assertThat(contracts.findByIdAndOwnerSub(contract.id(), "sub-b")).isEmpty();
    assertThat(contracts.findByIdAndOwnerSub(UUID.randomUUID(), "sub-a")).isEmpty();
  }

  @Test
  void databaseRejectsMoreThanThirtyPages() {
    contracts.save(new Contract("sub-a", "big.pdf", 31, "text"));
    assertThatThrownBy(em::flush).hasMessageContaining("page_count");
  }

  @Test
  void databaseRejectsUnknownLanguageAndStatus() {
    Contract contract = contracts.save(new Contract("sub-a", "a.pdf", 1, "text"));
    em.flush();
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        "INSERT INTO mizan.analyses (id, contract_id, language, status)"
                            + " VALUES (gen_random_uuid(), ?, 'en', 'PENDING')")
                    .param(contract.id())
                    .update())
        .hasMessageContaining("language");
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        "INSERT INTO mizan.analyses (id, contract_id, language, status)"
                            + " VALUES (gen_random_uuid(), ?, 'fr', 'DONE')")
                    .param(contract.id())
                    .update())
        .hasMessageContaining("status");
  }

  @Test
  void deletingAContractCascadesToItsAnalyses() {
    Contract contract = contracts.save(new Contract("sub-a", "a.pdf", 1, "text"));
    analyses.save(new Analysis(contract, Language.FR));
    analyses.save(new Analysis(contract, Language.AR));
    em.flush();

    jdbc.sql("DELETE FROM mizan.contracts WHERE id = ?").param(contract.id()).update();

    assertThat(
            jdbc.sql("SELECT count(*) FROM mizan.analyses WHERE contract_id = ?")
                .param(contract.id())
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  void languageCodesRoundTrip() {
    assertThat(Language.fromCode("ary")).contains(Language.ARY);
    assertThat(Language.fromCode("en")).isEmpty();
    Language.JpaConverter converter = new Language.JpaConverter();
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
    assertThat(converter.convertToEntityAttribute("ar")).isEqualTo(Language.AR);
  }
}
