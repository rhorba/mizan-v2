package ma.mizan.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "ma.mizan.api", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

  @ArchTest
  static final ArchRule modulesAreFreeOfCycles =
      slices().matching("ma.mizan.api.(*)..").should().beFreeOfCycles().allowEmptyShould(true);

  @ArchTest
  static final ArchRule controllersDoNotUseRepositories =
      noClasses()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .should()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("Repository")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule sharedDependsOnNoFeatureModule =
      noClasses()
          .that()
          .resideInAPackage("ma.mizan.api.shared..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "ma.mizan.api.contracts..", "ma.mizan.api.quota..", "ma.mizan.api.admin..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule noFieldInjection =
      noClasses()
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.springframework.beans.factory.annotation.Autowired")
          .allowEmptyShould(true);
}
