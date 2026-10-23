package ma.mizan.api.contracts;

class UploadRejectedException extends RuntimeException {

  private final UploadProblem problem;

  UploadRejectedException(UploadProblem problem) {
    super(problem.title());
    this.problem = problem;
  }

  UploadProblem problem() {
    return problem;
  }
}
