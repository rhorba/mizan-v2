package ma.mizan.api.contracts;

class ContractProblemException extends RuntimeException {

  private final ContractProblem problem;

  ContractProblemException(ContractProblem problem) {
    super(problem.title());
    this.problem = problem;
  }

  ContractProblem problem() {
    return problem;
  }
}
