package ma.mizan.analyzer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "ma.mizan.analyzer",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

  @ArchTest
  static final ArchRule packagesAreFreeOfCycles =
      slices().matching("ma.mizan.analyzer.(*)..").should().beFreeOfCycles().allowEmptyShould(true);

  /** ADR-1: the analyzer is stateless and owns no database. */
  @ArchTest
  static final ArchRule noDatabaseAccess =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("java.sql..", "jakarta.persistence..", "org.springframework.jdbc..")
          .allowEmptyShould(true);
}
