package ma.mizan.api;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

  static final ApplicationModules MODULES = ApplicationModules.of(MizanApiApplication.class);

  @Test
  void modulesRespectBoundaries() {
    MODULES.verify();
  }

  @Test
  void writesModuleDocumentation() {
    new Documenter(MODULES).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
  }
}
